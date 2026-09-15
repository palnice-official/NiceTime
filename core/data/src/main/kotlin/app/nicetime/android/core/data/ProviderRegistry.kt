package app.nicetime.android.core.data

import app.nicetime.android.core.model.ContentProvider
import app.nicetime.android.core.model.MediaSummary
import app.nicetime.android.core.model.Page
import app.nicetime.android.core.model.ProviderFailure
import app.nicetime.android.core.model.ProviderInstanceId
import app.nicetime.android.core.model.ProviderResult
import app.nicetime.android.core.model.SearchRequest
import app.nicetime.android.core.model.CatalogRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class ProviderSearchResult(
    val provider: ProviderInstanceId,
    val result: ProviderResult<Page<MediaSummary>>,
)

/**
 * Routes one query to enabled providers while containing timeout/source failures.
 * Each query gets a generation ID; stale queries are cancelled when a newer one starts.
 * Supports pagination with cursor-based deduplication and cache freshness tracking.
 */
class ProviderRegistry(
    providers: List<ContentProvider>,
    maxConcurrentRequests: Int = 4,
) {
    private val providersById = providers.associateBy { it.instanceId }
    private val semaphore = Semaphore(maxConcurrentRequests)
    private val latestQueryGeneration = AtomicLong(0)
    // ponytail: cache freshness tracking via timestamp per provider+query
    private val cacheTimestamps = ConcurrentHashMap<String, Long>()
    private val cacheTtlMs = 60_000L // 1 minute default TTL

    fun provider(id: ProviderInstanceId): ContentProvider? = providersById[id]

    /**
     * Returns a generation ID for this query. Callers should check
     * [isCurrentGeneration] before consuming results to discard stale responses.
     */
    fun beginQuery(): Long = latestQueryGeneration.incrementAndGet()

    fun isCurrentGeneration(generation: Long): Boolean = generation == latestQueryGeneration.get()

    /** Check if cached result for this provider+query is still fresh. */
    fun isCacheFresh(providerId: ProviderInstanceId, query: String): Boolean {
        val key = "${providerId}|${query}"
        val timestamp = cacheTimestamps[key] ?: return false
        return System.currentTimeMillis() - timestamp < cacheTtlMs
    }

    /** Mark cache as fresh for this provider+query. */
    private fun markCacheFresh(providerId: ProviderInstanceId, query: String) {
        cacheTimestamps["${providerId}|${query}"] = System.currentTimeMillis()
    }

    /** Emit each source immediately; semaphore wait counts toward the source deadline. */
    fun searchResults(request: SearchRequest, timeoutMs: Long = 10_000,
        generation: Long = latestQueryGeneration.get()): Flow<ProviderSearchResult> = channelFlow {
        providersById.values.filter { it.capabilities.search }.forEach { provider ->
            launch {
                val result = try {
                    withTimeoutOrNull(timeoutMs) {
                        semaphore.withPermit { provider.search(request) }
                    } ?: ProviderResult.Failure(ProviderFailure.Timeout())
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: java.io.IOException) { ProviderResult.Failure(ProviderFailure.NetworkUnavailable()) }
                catch (_: Exception) { ProviderResult.Failure(ProviderFailure.SourceUnavailable()) }
                currentCoroutineContext().ensureActive()
                if (isCurrentGeneration(generation)) {
                    result.onSuccess { markCacheFresh(provider.instanceId, request.query) }
                    send(ProviderSearchResult(provider.instanceId, result.map { page ->
                        page.copy(items = page.items.filter { it.ref.provider == provider.instanceId }.distinctBy { it.ref })
                    }))
                }
            }
        }
    }

    /**
     * Searches all providers with timeout isolation. The [generation] parameter
     * allows callers to discard results from queries that are no longer current.
     * If the caller's generation is stale before results arrive, the coroutine
     * context is checked and results may be incomplete.
     */
    suspend fun searchAll(
        request: SearchRequest,
        timeoutMs: Long = 15_000,
        generation: Long = latestQueryGeneration.get(),
    ): List<ProviderSearchResult> =
        searchResults(request, timeoutMs, generation).toList()

    /** Search with pagination support. Returns results and whether more pages are available. */
    suspend fun searchPaged(
        request: SearchRequest,
        timeoutMs: Long = 15_000,
        generation: Long = latestQueryGeneration.get(),
    ): Pair<List<ProviderSearchResult>, Map<ProviderInstanceId, String?>> {
        val results = searchAll(request, timeoutMs, generation)
        val cursors = results.associate { result ->
            result.provider to when (result.result) {
                is ProviderResult.Ok -> (result.result as ProviderResult.Ok).value.nextCursor
                is ProviderResult.Failure -> null
            }
        }
        return results to cursors
    }

    /** Fetch next page for a specific provider using its cursor. */
    suspend fun fetchNextPage(
        providerId: ProviderInstanceId,
        cursor: String,
        timeoutMs: Long = 10_000,
    ): ProviderSearchResult? {
        val provider = providersById[providerId] ?: return null
        if (!provider.capabilities.search) return null
        val result = try {
            withTimeoutOrNull(timeoutMs) {
                semaphore.withPermit {
                    // ponytail: reuse catalog with cursor-based page tracking
                    provider.search(SearchRequest(query = cursor, page = cursor.toIntOrNull() ?: 2))
                }
            } ?: ProviderResult.Failure(ProviderFailure.Timeout())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: java.io.IOException) { ProviderResult.Failure(ProviderFailure.NetworkUnavailable()) }
        catch (_: Exception) { ProviderResult.Failure(ProviderFailure.SourceUnavailable()) }
        return ProviderSearchResult(providerId, result.map { page ->
            page.copy(items = page.items.filter { it.ref.provider == providerId }.distinctBy { it.ref })
        })
    }

    suspend fun catalogAll(timeoutMs: Long = 15_000): List<ProviderSearchResult> =
        catalogPaged(timeoutMs).first

    /** Catalog with pagination support. Returns results and next cursors per provider. */
    suspend fun catalogPaged(
        timeoutMs: Long = 15_000,
        page: Int = 1,
    ): Pair<List<ProviderSearchResult>, Map<ProviderInstanceId, String?>> = supervisorScope {
        val results = providersById.values.map { provider ->
            async {
                val result = try {
                    semaphore.withPermit {
                        withTimeout(timeoutMs) {
                            provider.catalog(CatalogRequest(page = page))
                        }
                    }
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    ProviderResult.Failure(ProviderFailure.Timeout())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: java.io.IOException) {
                    ProviderResult.Failure(ProviderFailure.NetworkUnavailable())
                } catch (_: Exception) {
                    ProviderResult.Failure(ProviderFailure.SourceUnavailable())
                }
                if (result is ProviderResult.Ok) markCacheFresh(provider.instanceId, "catalog")
                ProviderSearchResult(provider.instanceId, result)
            }
        }.awaitAll()
        val cursors = results.associate { result ->
            result.provider to when (result.result) {
                is ProviderResult.Ok -> (result.result as ProviderResult.Ok).value.nextCursor
                is ProviderResult.Failure -> null
            }
        }
        results to cursors
    }

    /** Clear all cached freshness timestamps. */
    fun clearCache() {
        cacheTimestamps.clear()
    }
}

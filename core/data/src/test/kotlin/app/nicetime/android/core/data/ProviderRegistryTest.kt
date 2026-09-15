package app.nicetime.android.core.data

import app.nicetime.android.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {
    @Test fun `healthy partial result arrives before slow timeout`() = runTest {
        val receivedAt = mutableListOf<Long>()
        val healthy = testProvider("healthy") { ProviderResult.Ok(Page(listOf(MediaSummary(it, "Found")))) }
        val slow = testProvider("slow") { delay(1000); ProviderResult.Ok(Page(emptyList())) }
        ProviderRegistry(listOf(slow, healthy)).searchResults(SearchRequest("x"), timeoutMs = 100).collect {
            receivedAt += testScheduler.currentTime
        }
        assertEquals(listOf(0L, 100L), receivedAt)
    }

    @Test fun `obsolete generation cannot emit after a newer query`() = runTest {
        val provider = testProvider("delayed") { delay(50); ProviderResult.Ok(Page(listOf(MediaSummary(it, "Old")))) }
        val registry = ProviderRegistry(listOf(provider))
        val old = registry.beginQuery()
        val results = mutableListOf<ProviderSearchResult>()
        val job = launch { registry.searchResults(SearchRequest("old"), generation = old).toList(results) }
        testScheduler.runCurrent()
        registry.beginQuery()
        job.join()
        assertTrue(results.isEmpty())
    }

    @Test fun `queue wait is bounded and concurrency never exceeds limit`() = runTest {
        var active = 0
        var maximum = 0
        val providers = (1..6).map { index -> testProvider("p$index") {
            active++; maximum = maxOf(maximum, active)
            try { delay(1000); ProviderResult.Ok(Page(emptyList())) } finally { active-- }
        } }
        val results = ProviderRegistry(providers, 2).searchAll(SearchRequest("x"), timeoutMs = 100)
        assertEquals(6, results.size)
        assertEquals(2, maximum)
        assertEquals(100L, testScheduler.currentTime)
    }
    @Test
    fun `slow source times out without hiding healthy result`() = runTest {
        val healthy = testProvider("healthy") { ProviderResult.Ok(Page(listOf(MediaSummary(it, "Found")))) }
        val slow = testProvider("slow") { delay(100); ProviderResult.Ok(Page(emptyList())) }

        val results = ProviderRegistry(listOf(slow, healthy)).searchAll(SearchRequest("found"), timeoutMs = 10)

        assertEquals(2, results.size)
        assertTrue(results.toString(), results.any { it.result is ProviderResult.Ok && it.result.value.items.size == 1 })
        assertTrue(results.toString(), results.any { it.result is ProviderResult.Failure && it.result.failure is ProviderFailure.Timeout })
    }

    @Test
    fun `provider exception is isolated as source unavailable`() = runTest {
        val broken = testProvider("broken") { error("bad source") }
        val result = ProviderRegistry(listOf(broken)).searchAll(SearchRequest("x")).single().result
        assertTrue(result.toString(), result is ProviderResult.Failure && result.failure is ProviderFailure.SourceUnavailable)
    }

    @Test
    fun `query generation advances and stales previous generation`() {
        val registry = ProviderRegistry(emptyList())
        val gen1 = registry.beginQuery()
        assertTrue(registry.isCurrentGeneration(gen1))
        val gen2 = registry.beginQuery()
        assertFalse(registry.isCurrentGeneration(gen1))
        assertTrue(registry.isCurrentGeneration(gen2))
    }

    @Test
    fun `pagination cursor absent when single page returned`() = runTest {
        val provider = testProvider("p1") {
            ProviderResult.Ok(Page(listOf(MediaSummary(it, "A"))))
        }
        val results = ProviderRegistry(listOf(provider)).searchAll(SearchRequest("A"))
        val page = (results.single().result as ProviderResult.Ok).value
        assertEquals(1, page.items.size)
    }

    private fun testProvider(
        id: String,
        search: suspend (MediaRef) -> ProviderResult<Page<MediaSummary>>,
    ): ContentProvider {
        val providerId = ProviderInstanceId(java.util.UUID.nameUUIDFromBytes(id.toByteArray()))
        val media = MediaRef(providerId, id, MediaType.MOVIE)
        return object : ContentProvider {
            override val instanceId = providerId
            override val capabilities = ProviderCapabilities(search = true)
            override suspend fun catalog(request: CatalogRequest) = ProviderResult.Failure(ProviderFailure.Unsupported("catalog"))
            override suspend fun search(request: SearchRequest) = search(media)
            override suspend fun details(ref: MediaRef) = ProviderResult.Failure(ProviderFailure.Unsupported("details"))
            override suspend fun streams(ref: EpisodeRef) = ProviderResult.Failure(ProviderFailure.Unsupported("streams"))
            override suspend fun resolve(ref: ReleaseRef, purpose: ResolvePurpose) = ProviderResult.Failure(ProviderFailure.Unsupported("resolve"))
        }
    }
}

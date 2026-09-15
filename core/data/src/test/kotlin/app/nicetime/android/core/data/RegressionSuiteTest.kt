package app.nicetime.android.core.data

import app.nicetime.android.core.model.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RegressionSuiteTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun addonClient_rejectsMalformedJson() = runTest {
        server.enqueue(MockResponse().setBody("{broken"))
        val result = AddonClient().fetchManifest(
            server.url("/manifest.json").newBuilder().host("127.0.0.1").build().toString(),
        )
        assertTrue(result is ProviderResult.Failure)
    }

    @Test
    fun addonClient_rejectsMissingRequiredFields() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"x"}"""))
        val result = AddonClient().fetchManifest(
            server.url("/manifest.json").newBuilder().host("127.0.0.1").build().toString(),
        )
        assertTrue(result is ProviderResult.Failure)
    }

    @Test
    fun addonClient_rejectsNon200Status() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        val result = AddonClient().fetchManifest(
            server.url("/manifest.json").newBuilder().host("127.0.0.1").build().toString(),
        )
        assertTrue(result is ProviderResult.Failure)
    }

    @Test
    fun providerRegistry_isolatesProviderException() = runTest {
        val broken = object : ContentProvider {
            override val instanceId = ProviderInstanceId.new()
            override val capabilities = ProviderCapabilities(search = true)
            override suspend fun catalog(request: CatalogRequest) = ProviderResult.Failure(ProviderFailure.Unsupported(""))
            override suspend fun search(request: SearchRequest) = error("boom")
            override suspend fun details(ref: MediaRef) = ProviderResult.Failure(ProviderFailure.Unsupported(""))
            override suspend fun streams(ref: EpisodeRef) = ProviderResult.Failure(ProviderFailure.Unsupported(""))
            override suspend fun resolve(ref: ReleaseRef, purpose: ResolvePurpose) = ProviderResult.Failure(ProviderFailure.Unsupported(""))
        }
        val healthyId = ProviderInstanceId.new()
        val healthy = object : ContentProvider {
            override val instanceId = healthyId
            override val capabilities = ProviderCapabilities(search = true)
            override suspend fun catalog(request: CatalogRequest) = ProviderResult.Failure(ProviderFailure.Unsupported(""))
            override suspend fun search(request: SearchRequest) = ProviderResult.Ok(Page(listOf(MediaSummary(MediaRef(healthyId, "ok", MediaType.MOVIE), "OK"))))
            override suspend fun details(ref: MediaRef) = ProviderResult.Failure(ProviderFailure.Unsupported(""))
            override suspend fun streams(ref: EpisodeRef) = ProviderResult.Failure(ProviderFailure.Unsupported(""))
            override suspend fun resolve(ref: ReleaseRef, purpose: ResolvePurpose) = ProviderResult.Failure(ProviderFailure.Unsupported(""))
        }
        val results = ProviderRegistry(listOf(broken, healthy)).searchAll(SearchRequest("x"))
        assertEquals(2, results.size)
        assertTrue(results.any { it.result is ProviderResult.Failure })
        assertTrue(results.any { it.result is ProviderResult.Ok && it.result.value.items.size == 1 })
    }

    @Test
    fun stremioProvider_rejectsStreamFromDifferentProvider() = runTest {
        val id = ProviderInstanceId.new()
        val provider = StremioAddonProvider(id, "https://example.test/manifest.json", AddonManifest("t", name = "T"))
        val otherId = ProviderInstanceId.new()
        val ref = ReleaseRef(EpisodeRef(MediaRef(otherId, "x", MediaType.MOVIE), "x", null, null), "y")
        assertTrue(provider.resolve(ref, ResolvePurpose.STREAM) is ProviderResult.Failure)
    }

    @Test
    fun movieBoxProvider_blankSearch_returnsEmpty() = runTest {
        server.enqueue(MockResponse().setBody("""{"token":"t","uid":"u"}"""))
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        val provider = MovieBoxProvider(hosts = listOf(server.url("/").toString()))
        val result = provider.search(SearchRequest(""))
        assertTrue(result is ProviderResult.Ok)
        assertEquals(0, (result as ProviderResult.Ok).value.items.size)
    }

    @Test
    fun fourKHDHubProvider_searchBlank_returnsEmpty() = runTest {
        val provider = FourKHDHubProvider(http = okhttp3.OkHttpClient(), baseUrl = server.url("/").toString())
        val result = provider.search(SearchRequest(""))
        assertTrue(result is ProviderResult.Ok)
        assertEquals(0, (result as ProviderResult.Ok).value.items.size)
    }

    @Test
    fun diagnostics_redactsAuthTokens() {
        val input = "Bearer eyJhbGciOiJIUzI1NiJ9.secret.token url?token=abc123"
        val output = redactSensitiveText(input)
        assertFalse(output.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(output.contains("Bearer [REDACTED]"))
        assertTrue(output.contains("token=[REDACTED]"))
    }

    @Test
    fun diagnosticLog_evictsOldestOnOverflow() {
        val log = DiagnosticLog(capacity = 3)
        log.record("a"); log.record("b"); log.record("c"); log.record("d")
        val exported = log.export()
        assertFalse(exported.contains("a"))
        assertTrue(exported.contains("d"))
    }
}

package app.nicetime.android.core.data

import app.nicetime.android.core.model.ProviderFailure
import app.nicetime.android.core.model.ProviderResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonClientTest {
    @Test
    fun `normalizes secure addon root and removes fragment`() {
        assertEquals("https://example.test/addon/manifest.json?token=x", AddonClient.normalizeManifestUrl("https://example.test/addon?token=x#fragment"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects insecure remote endpoint`() {
        AddonClient.normalizeManifestUrl("http://example.test/addon")
    }

    @Test
    fun `parses a bounded valid manifest`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":"demo","version":"1.0.0","name":"Demo","types":["movie"]}"""))
            val rawUrl = server.url("/manifest.json").newBuilder().host("127.0.0.1").build().toString()
            runCatching { AddonClient.normalizeManifestUrl(rawUrl) }
                .getOrElse { throw AssertionError("Could not normalize $rawUrl: ${it.message}", it) }
            val result = AddonClient().fetchManifest(rawUrl)
            val message = (result as? ProviderResult.Failure)?.failure?.message ?: result.toString()
            assertTrue(message, result is ProviderResult.Ok)
            assertEquals("Demo", (result as ProviderResult.Ok).value.name)
        }
    }

    @Test
    fun `rejects oversized manifest`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(AddonClient.MAX_MANIFEST_BYTES + 1)))
            val result = AddonClient().fetchManifest(
                server.url("/manifest.json").newBuilder().host("127.0.0.1").build().toString(),
            )
            assertTrue(result is ProviderResult.Failure)
            assertTrue((result as ProviderResult.Failure).failure is ProviderFailure.ParseFailure)
        }
    }
}

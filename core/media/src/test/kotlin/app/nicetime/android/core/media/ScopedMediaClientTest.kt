package app.nicetime.android.core.media

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class ScopedMediaClientTest {

    /** A redirect that leaves the declared scope is still followed (playback depends on it),
     *  but it must never carry the source's credentials with it. */
    @Test fun `redirect outside scope is followed without source credentials`() {
        MockWebServer().use { origin -> MockWebServer().use { other ->
            origin.start(); other.start()
            val target = other.url("/cdn-segment").newBuilder().host("127.0.0.1").build()
            origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", target))
            other.enqueue(MockResponse().setBody("video"))
            val uri = origin.url("/movie").toString()
            val client = scopedMediaClient(uri, mapOf("Cookie" to "private-cookie", "User-Agent" to "NiceTime/0.1"), emptySet())
            client.newCall(Request.Builder().url(uri).build()).execute().use {
                assertEquals("video", it.body!!.string())
            }
            // The origin is in scope and gets the full request context...
            assertEquals("private-cookie", origin.takeRequest()?.getHeader("Cookie"))
            // ...the off-scope host is reached, but with no cookie and no referer.
            val offScope: RecordedRequest? = other.takeRequest(1, TimeUnit.SECONDS)
            assertNotNull("Off-scope host should be reachable for CDN segments", offScope)
            assertNull("Credentials must not leak off-scope", offScope?.getHeader("Cookie"))
            assertNull("Referer must not leak off-scope", offScope?.getHeader("Referer"))
            assertEquals("NiceTime/0.1", offScope?.getHeader("User-Agent"))
        } }
    }

    /** A same-host redirect is an ordinary adaptive-segment hop, so context stays intact. */
    @Test fun `relative redirects keep headers within source scope`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/segment"))
            server.enqueue(MockResponse().setBody("video"))
            val uri = server.url("/manifest").toString()
            scopedMediaClient(uri, mapOf("Cookie" to "source-a"), emptySet())
                .newCall(Request.Builder().url(uri).build()).execute().use { assertEquals("video", it.body!!.string()) }
            assertEquals("source-a", server.takeRequest()?.getHeader("Cookie"))
            assertEquals("source-a", server.takeRequest()?.getHeader("Cookie"))
        }
    }

    @Test fun `two clients on same host do not share request contexts`() {
        MockWebServer().use { server ->
            server.start()
            repeat(2) { server.enqueue(MockResponse().setBody("ok")) }
            val uri = server.url("/video").toString()
            for (secret in listOf("a", "b")) scopedMediaClient(uri, mapOf("Cookie" to secret), emptySet())
                .newCall(Request.Builder().url(uri).build()).execute().close()
            assertEquals("a", server.takeRequest()?.getHeader("Cookie"))
            assertEquals("b", server.takeRequest()?.getHeader("Cookie"))
        }
    }

    /** Declared hosts always keep the full request context, even mid-redirect. */
    @Test fun `explicitly declared sibling host keeps credentials across a redirect`() {
        MockWebServer().use { origin -> MockWebServer().use { cdn ->
            origin.start(); cdn.start()
            origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", cdn.url("/seg.ts")))
            cdn.enqueue(MockResponse().setBody("segment"))
            val originUrl = origin.url("/master.m3u8").toString()
            val cdnHost = cdn.url("/").host
            val client = scopedMediaClient(originUrl, mapOf("Cookie" to "signed"), setOf(origin.url("/").host, cdnHost))
            client.newCall(Request.Builder().url(originUrl).build()).execute().close()
            assertEquals("signed", origin.takeRequest()?.getHeader("Cookie"))
            assertEquals("signed", cdn.takeRequest(1, TimeUnit.SECONDS)?.getHeader("Cookie"))
        } }
    }

    /** An origin outside its own declared scope is rejected before any network hop. */
    @Test fun `origin outside declared scope is rejected`() {
        val uri = "https://origin.example.com/movie.m3u8"
        val error = runCatching { scopedMediaClient(uri, emptyMap(), setOf("other.example.com")) }.exceptionOrNull()
        assertTrue("Expected a rejection, got $error", error is IOException)
    }
}

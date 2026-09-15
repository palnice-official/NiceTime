package app.nicetime.android.core.media

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class ScopedMediaClientTest {
    @Test fun `redirect outside scope never receives request or cookies`() {
        MockWebServer().use { origin -> MockWebServer().use { other ->
            origin.start(); other.start()
            val target = other.url("/stolen").newBuilder().host("127.0.0.1").build()
            origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", target))
            val uri = origin.url("/movie").toString()
            val client = scopedMediaClient(uri, mapOf("Cookie" to "private-cookie"), emptySet())
            try { client.newCall(Request.Builder().url(uri).build()).execute().close(); fail("Redirect must fail") }
            catch (_: IOException) { }
            assertEquals("private-cookie", origin.takeRequest().getHeader("Cookie"))
            assertNull(other.takeRequest(100, TimeUnit.MILLISECONDS))
        } }
    }

    @Test fun `relative redirects keep headers within source scope`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/segment"))
            server.enqueue(MockResponse().setBody("video"))
            val uri = server.url("/manifest").toString()
            scopedMediaClient(uri, mapOf("Cookie" to "source-a"), emptySet())
                .newCall(Request.Builder().url(uri).build()).execute().use { assertEquals("video", it.body!!.string()) }
            assertEquals("source-a", server.takeRequest().getHeader("Cookie"))
            assertEquals("source-a", server.takeRequest().getHeader("Cookie"))
        }
    }

    @Test fun `two clients on same host do not share request contexts`() {
        MockWebServer().use { server ->
            server.start()
            repeat(2) { server.enqueue(MockResponse().setBody("ok")) }
            val uri = server.url("/video").toString()
            for (secret in listOf("a", "b")) scopedMediaClient(uri, mapOf("Cookie" to secret), emptySet())
                .newCall(Request.Builder().url(uri).build()).execute().close()
            assertEquals("a", server.takeRequest().getHeader("Cookie"))
            assertEquals("b", server.takeRequest().getHeader("Cookie"))
        }
    }
}

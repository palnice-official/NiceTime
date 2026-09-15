package app.nicetime.android.core.data

import app.nicetime.android.core.model.*
import java.util.Base64
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MovieBoxProviderTest {
    private lateinit var server: MockWebServer
    private val clock = 1_700_000_000_000L

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { server.shutdown() }

    @Test fun `signing matches Rust parity vector`() {
        assertEquals("1700000000000,e41bb805cc23fdc5541917da93d608d5", MovieBoxCrypto.clientToken(clock))
        assertEquals(
            "1700000000000|2|+F/nG/52naoh5xjfG63Wvw==",
            MovieBoxCrypto.signature("GET", "application/json", "application/json", "https://api.example.com/path?tab=1&page=2", null, clock),
        )
    }

    @Test fun `jwt session uses native user id and expiry`() {
        val claims = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"userId\":12345,\"exp\":1893456000}".toByteArray())
        val session = MovieBoxSession.fromToken("header.$claims.signature", null, clock / 1000)
        assertEquals("12345", session.userId)
        assertEquals(1_893_456_000L, session.expiresAt)
        assertTrue(session.isValid(clock / 1000))
    }

    @Test fun `provider preserves media episode release identity and scoped headers`() = runTest {
        server.enqueue(json("""{"token":"guest-token","uid":"u1"}"""))
        server.enqueue(json("""{"results":[{"subjects":[{"subjectId":"500","title":"Money Heist","subjectType":2,"releaseDate":"2017","cover":{"url":"https://img.example/poster.jpg"}}]}]}"""))
        server.enqueue(json("""{"subject":{"subjectId":"500","title":"Money Heist","subjectType":2,"description":"A heist series","seasons":{"seasons":[{"se":1,"episodeNumbers":[1,2]}]}}}"""))
        val policy = Base64.getEncoder().encodeToString("{\"Statement\":[{\"Resource\":\"https://cdn.example/dash/500_1_1/*\"}]}".toByteArray())
        server.enqueue(json("""{"title":"Money Heist","displayResolutions":"480,720,1080","streams":[{"id":"release-9","format":"DASH","codecName":"hevc","size":"1500000000","url":"https://cdn.example/notice.mp4","signCookie":"CloudFront-Policy=$policy; CloudFront-Signature=abc;"}]}"""))

        val provider = MovieBoxProvider(http = OkHttpClient(), hosts = listOf(server.url("/").toString()), nowMs = { clock }, userAgent = "TestAgent/1.0")
        val found = (provider.search(SearchRequest("Money Heist")) as ProviderResult.Ok).value.items.single()
        assertEquals("500", found.ref.providerMediaId)
        assertEquals(MediaType.SERIES, found.ref.type)

        val details = (provider.details(found.ref) as ProviderResult.Ok).value
        val episode = details.seasons.single().episodes.first().ref
        assertEquals("500:1:1", episode.providerEpisodeId)

        val release = (provider.streams(episode) as ProviderResult.Ok).value.single()
        assertEquals("release-9", release.ref.providerReleaseId)
        assertNull(release.resolutionHeight)
        val stream = (provider.resolve(release.ref, ResolvePurpose.DOWNLOAD) as ProviderResult.Ok).value
        assertEquals("https://cdn.example/dash/500_1_1/index.mpd", stream.uri)
        assertEquals("CloudFront-Policy=$policy; CloudFront-Signature=abc", stream.requestHeaders["Cookie"])
        assertEquals(setOf("cdn.example"), stream.allowedHosts)

        val login = server.takeRequest()
        assertEquals("/wefeed-mobile-bff/user-api/visitor-login", login.path)
        assertNotNull(login.getHeader("X-TR-Signature"))
        val search = server.takeRequest()
        assertEquals("Bearer guest-token", search.getHeader("Authorization"))
        assertEquals("/wefeed-mobile-bff/subject-api/search/v2", search.path)
        assertEquals(4, server.requestCount)
    }

    @Test fun `notice stream without signed policy is rejected`() = runTest {
        server.enqueue(json("""{"token":"guest-token"}"""))
        server.enqueue(json("""{"title":"Notice","streams":[{"id":"1","url":"https://macdn.aoneroom.com/other/notice.mp4","resolutions":"480"}]}"""))
        val provider = MovieBoxProvider(hosts = listOf(server.url("/").toString()), nowMs = { clock })
        val media = MediaRef(provider.instanceId, "1", MediaType.MOVIE)
        val episode = EpisodeRef(media, "1", null, null)
        assertTrue((provider.streams(episode) as ProviderResult.Ok).value.isEmpty())
    }

    @Test fun `missing guest token is an auth failure`() = runTest {
        server.enqueue(json("{}"))
        val provider = MovieBoxProvider(hosts = listOf(server.url("/").toString()), nowMs = { clock })
        val result = provider.search(SearchRequest("anything"))
        assertTrue(result is ProviderResult.Failure && result.failure is ProviderFailure.AuthExpired)
    }

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
}

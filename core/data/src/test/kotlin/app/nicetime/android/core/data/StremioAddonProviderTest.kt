package app.nicetime.android.core.data

import app.nicetime.android.core.model.*
import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioAddonProviderTest {
    @Test
    fun `search details streams and resolve preserve provider identity`() = runTest {
        MockWebServer().use { server ->
            val root = server.url("/").newBuilder().host("127.0.0.1").build().toString().trimEnd('/')
            val providerId = ProviderInstanceId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
            val provider = StremioAddonProvider(
                instanceId = providerId,
                manifestUrl = "$root/manifest.json",
                manifest = AddonManifest(
                    id = "fixture",
                    name = "Fixture addon",
                    types = listOf("movie"),
                    catalogs = listOf(AddonCatalog("movie", "top")),
                ),
            )

            server.enqueue(MockResponse().setBody("""{"metas":[{"id":"tt123","type":"movie","name":"Example","year":"2024","poster":"https://images.test/p.jpg"}]}"""))
            val search = provider.search(SearchRequest("Example")) as ProviderResult.Ok
            val media = search.value.items.single()
            assertEquals(providerId, media.ref.provider)
            assertEquals("/catalog/movie/top/search=Example.json", server.takeRequest().path)

            server.enqueue(MockResponse().setBody("""{"meta":{"id":"tt123","type":"movie","name":"Example","description":"Summary"}}"""))
            val details = provider.details(media.ref) as ProviderResult.Ok
            assertEquals("Summary", details.value.synopsis)

            server.enqueue(MockResponse().setBody("""{"streams":[{"name":"Fixture 1080p","description":"1.5 GB","url":"https://media.example.test/video.mp4"},{"name":"Unsupported","infoHash":"abc"}]}"""))
            val episode = EpisodeRef(media.ref, "tt123", null, null)
            val streams = provider.streams(episode) as ProviderResult.Ok
            assertEquals(1, streams.value.size)
            assertEquals(1080, streams.value.single().resolutionHeight)
            assertEquals(1_610_612_736L, streams.value.single().sizeBytes)
            assertTrue(streams.value.single().downloadable)

            val resolved = provider.resolve(streams.value.single().ref, ResolvePurpose.STREAM) as ProviderResult.Ok
            assertEquals("media.example.test", resolved.value.allowedHosts.single())
            assertEquals("https://media.example.test/video.mp4", resolved.value.uri)
        }
    }

    @Test
    fun `rejects a release that was not produced by this provider`() = runTest {
        val id = ProviderInstanceId.new()
        val provider = StremioAddonProvider(
            id,
            "https://example.test/manifest.json",
            AddonManifest("fixture", name = "Fixture"),
        )
        val media = MediaRef(id, "movie", MediaType.MOVIE)
        val unknown = ReleaseRef(EpisodeRef(media, "movie", null, null), "unknown")
        assertTrue(provider.resolve(unknown, ResolvePurpose.STREAM) is ProviderResult.Failure)
    }
}

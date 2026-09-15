package app.nicetime.android.core.model

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentProviderContractTest {

    private fun movie(provider: ProviderInstanceId, id: String = "m1") =
        MediaRef(provider, id, MediaType.MOVIE)

    @Test
    fun `capability not declared returns unsupported not crash`() = runTest {
        val provider = FakeProvider(capabilities = ProviderCapabilities())
        val result = provider.catalog(CatalogRequest())
        assertTrue(result is ProviderResult.Failure)
        assertTrue((result as ProviderResult.Failure).failure is ProviderFailure.Unsupported)
    }

    @Test
    fun `cancellation stays cancellation`() = runTest {
        val providerId = ProviderInstanceId.new()
        val provider = FakeProvider(
            instanceId = providerId,
            catalogPages = listOf(listOf(MediaSummary(movie(providerId), "T"))),
        )
        val job = launch {
            provider.catalog(CatalogRequest())
        }
        job.cancel()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `download purpose requires download capability`() = runTest {
        val provider = FakeProvider(
            capabilities = ProviderCapabilities(streamResolution = true, downloadable = false),
            resolver = { ref, _ -> ResolvedStream(ref, "https://example.test/v.mp4", sourceLabel = "test") },
        )
        val media = movie(provider.instanceId)
        val episode = EpisodeRef(media, "m1", season = null, episode = null)
        val release = ReleaseRef(episode, "r1")
        val result = provider.resolve(release, ResolvePurpose.DOWNLOAD)
        assertTrue((result as ProviderResult.Failure).failure is ProviderFailure.Unsupported)

        val stream = provider.resolve(release, ResolvePurpose.STREAM)
        assertTrue(stream is ProviderResult.Ok)
    }

    @Test
    fun `pagination cursor present only when more pages exist`() = runTest {
        val providerId = ProviderInstanceId.new()
        val provider = FakeProvider(
            instanceId = providerId,
            catalogPages = listOf(
                listOf(MediaSummary(movie(providerId, "1"), "A")),
                listOf(MediaSummary(movie(providerId, "2"), "B")),
            ),
        )
        val page1 = provider.catalog(CatalogRequest(page = 1)) as ProviderResult.Ok
        val page2 = provider.catalog(CatalogRequest(page = 2)) as ProviderResult.Ok
        assertTrue(page1.value.nextCursor != null)
        assertTrue(page2.value.nextCursor == null)
        assertTrue(page3(provider).value.items.isEmpty())
    }

    private suspend fun page3(provider: FakeProvider) =
        provider.catalog(CatalogRequest(page = 3)) as ProviderResult.Ok
}

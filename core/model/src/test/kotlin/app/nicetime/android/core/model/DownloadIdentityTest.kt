package app.nicetime.android.core.model

import org.junit.Assert.*
import org.junit.Test

class DownloadIdentityTest {
    private val media = MediaRef(ProviderInstanceId.new(), "movie", MediaType.MOVIE)
    private val release = ReleaseRef(EpisodeRef(media, "episode", null, null), "release")

    @Test fun `same selection is stable and distinct selections stay separate`() {
        assertEquals(downloadIdentity(release), downloadIdentity(release.copy()))
        assertNotEquals(downloadIdentity(release), downloadIdentity(release, "audio:hi"))
        assertNotEquals(downloadIdentity(release), downloadIdentity(release.copy(episode = release.episode.copy(media = media.copy(type = MediaType.SERIES)))))
    }

    @Test fun `separator-bearing ids cannot alias another release`() {
        val first = release.copy(episode = release.episode.copy(providerEpisodeId = "a:b"), providerReleaseId = "c")
        val second = release.copy(episode = release.episode.copy(providerEpisodeId = "a"), providerReleaseId = "b:c")
        assertNotEquals(downloadIdentity(first), downloadIdentity(second))
    }
}

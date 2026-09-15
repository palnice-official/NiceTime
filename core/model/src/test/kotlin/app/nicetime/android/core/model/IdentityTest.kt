package app.nicetime.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class IdentityTest {

    @Test
    fun `two installations of same addon do not collide`() {
        val a = ProviderInstanceId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val b = ProviderInstanceId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val refA = MediaRef(a, "tt123", MediaType.MOVIE)
        val refB = MediaRef(b, "tt123", MediaType.MOVIE)
        assertNotEquals(refA, refB)
        assertNotEquals(refA.hashCode(), refB.hashCode())
    }

    @Test
    fun `movie and series ids with same native id stay distinct`() {
        val p = ProviderInstanceId.new()
        val movie = MediaRef(p, "x", MediaType.MOVIE)
        val series = MediaRef(p, "x", MediaType.SERIES)
        assertNotEquals(movie, series)
    }

    @Test
    fun `same titled content across providers does not merge`() {
        val p1 = ProviderInstanceId.new()
        val p2 = ProviderInstanceId.new()
        val s1 = MediaSummary(MediaRef(p1, "movie-1", MediaType.MOVIE), "Same Title")
        val s2 = MediaSummary(MediaRef(p2, "movie-9", MediaType.MOVIE), "Same Title")
        assertNotEquals(s1.ref, s2.ref)
    }

    @Test
    fun `episode ref retains season and episode identity`() {
        val media = MediaRef(ProviderInstanceId.new(), "series-7", MediaType.SERIES)
        val e1 = EpisodeRef(media, "s1e5", season = 1, episode = 5)
        val e2 = EpisodeRef(media, "s2e5", season = 2, episode = 5)
        assertNotEquals(e1, e2)
        assertEquals(1, e1.season)
        assertEquals(5, e1.episode)
    }
}

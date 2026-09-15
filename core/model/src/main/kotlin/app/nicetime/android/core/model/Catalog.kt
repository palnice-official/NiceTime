package app.nicetime.android.core.model

data class CatalogRequest(
    val page: Int = 1,
    val category: String? = null,
)

data class SearchRequest(
    val query: String,
    val page: Int = 1,
)

/** One page of provider results plus the provider's own cursor, if it supports pagination. */
data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)

data class MediaSummary(
    val ref: MediaRef,
    val title: String,
    val year: Int? = null,
    val posterUrl: String? = null,
)

data class MediaDetails(
    val ref: MediaRef,
    val title: String,
    val year: Int? = null,
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val seasons: List<Season> = emptyList(),
    val externalIds: List<ExternalId> = emptyList(),
    val runtimeMinutes: Int? = null,
)

data class Season(
    val providerSeasonId: String,
    val number: Int?,
    val episodes: List<EpisodeSummary> = emptyList(),
)

data class EpisodeSummary(
    val ref: EpisodeRef,
    val title: String?,
    val season: Int?,
    val episode: Int?,
    val synopsis: String? = null,
    val stillUrl: String? = null,
)

/** A distinct playable candidate for an episode. */
data class Release(
    val ref: ReleaseRef,
    val label: String,
    val resolutionHeight: Int? = null,
    val audioLanguage: String? = null,
    val container: String? = null,
    val sizeBytes: Long? = null,
    val downloadable: Boolean = false,
)

enum class ResolvePurpose { STREAM, DOWNLOAD }

/** Fully resolved, playable stream for one release. */
data class ResolvedStream(
    val release: ReleaseRef,
    val uri: String,
    val mimeType: String? = null,
    /** Headers scoped to this media item and its child requests. */
    val requestHeaders: Map<String, String> = emptyMap(),
    /** Hosts this stream's request context may talk to; empty = initial URI host only. */
    val allowedHosts: Set<String> = emptySet(),
    val expiresAtEpochMs: Long? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val sourceLabel: String,
)

data class SubtitleTrack(
    val uri: String,
    val language: String? = null,
    val mimeType: String? = null,
    val label: String? = null,
)

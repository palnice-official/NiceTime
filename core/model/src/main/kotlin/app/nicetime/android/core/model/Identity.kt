package app.nicetime.android.core.model

import java.util.UUID

/** Unique installation of a provider. Endpoint/credentials live in core:data, not here. */
data class ProviderInstanceId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun new() = ProviderInstanceId(UUID.randomUUID())
    }
}

/** Provider-native media identity: instance + native id + type. Never key by title or URL. */
data class MediaRef(
    val provider: ProviderInstanceId,
    val providerMediaId: String,
    val type: MediaType,
)

/** Series episode identity; movies use MediaRef directly with type Movie. */
data class EpisodeRef(
    val media: MediaRef,
    val providerEpisodeId: String,
    val season: Int?,
    val episode: Int?,
)

enum class MediaType { MOVIE, SERIES }

/** Verified external identity such as IMDb. Uncertain matches stay separate. */
data class ExternalId(val namespace: String, val value: String)

/** A distinct release/rendition of an episode, e.g. 1080p from one source. */
data class ReleaseRef(
    val episode: EpisodeRef,
    val providerReleaseId: String,
)

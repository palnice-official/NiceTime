package app.nicetime.android.core.model

import java.security.MessageDigest

fun downloadIdentity(ref: ReleaseRef, selection: String = "default"): String {
    val fields = listOf(ref.episode.media.provider.toString(), ref.episode.media.type.name,
        ref.episode.media.providerMediaId, ref.episode.providerEpisodeId,
        ref.episode.season?.toString().orEmpty(), ref.episode.episode?.toString().orEmpty(),
        ref.providerReleaseId, selection)
    val bytes = fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

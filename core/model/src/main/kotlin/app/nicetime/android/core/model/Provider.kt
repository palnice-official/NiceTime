package app.nicetime.android.core.model

/** What a provider installation can actually do; all optional beyond the basics. */
data class ProviderCapabilities(
    val catalog: Boolean = false,
    val search: Boolean = false,
    val pagination: Boolean = false,
    val streamResolution: Boolean = false,
    val downloadable: Boolean = false,
    val externalSubtitles: Boolean = false,
    val supportedTypes: Set<MediaType> = emptySet(),
    val liveTv: Boolean = false,
)

/** Why a provider call failed. Cancellation is never converted into this. */
sealed class ProviderFailure(val message: String) {
    class Timeout(message: String = "Source timed out") : ProviderFailure(message)
    class NetworkUnavailable(message: String = "Network unavailable") : ProviderFailure(message)
    class RateLimited(val retryAfterMs: Long?, message: String = "Source rate limited") : ProviderFailure(message)
    class AuthExpired(message: String = "Source credentials expired") : ProviderFailure(message)
    class NotFound(message: String = "Content not found") : ProviderFailure(message)
    class Unsupported(val reason: String) : ProviderFailure("Unsupported: $reason")
    class ParseFailure(message: String = "Could not read source response") : ProviderFailure(message)
    class SourceUnavailable(message: String = "Source unavailable") : ProviderFailure(message)
}

/** Result wrapper that keeps cancellation as cancellation, never as ordinary failure. */
sealed class ProviderResult<out T> {
    data class Ok<T>(val value: T) : ProviderResult<T>()
    data class Failure(val failure: ProviderFailure) : ProviderResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ProviderResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Failure -> this
    }

    inline fun onSuccess(block: (T) -> Unit): ProviderResult<T> {
        if (this is Ok) block(value)
        return this
    }

    inline fun onFailure(block: (ProviderFailure) -> Unit): ProviderResult<T> {
        if (this is Failure) block(failure)
        return this
    }

    fun getOrNull(): T? = (this as? Ok)?.value
}

fun <T> ProviderResult<T>.getOrDefault(default: T): T = when (this) {
    is ProviderResult.Ok -> value
    is ProviderResult.Failure -> default
}

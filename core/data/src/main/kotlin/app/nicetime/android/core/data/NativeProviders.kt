package app.nicetime.android.core.data

import app.nicetime.android.core.model.ProviderInstanceId
import java.util.UUID

/** Built-in installations keep the same identity and session across screens and searches. */
object NativeProviders {
    val movieBox by lazy { MovieBoxProvider(ProviderInstanceId(UUID.nameUUIDFromBytes("nicetime:moviebox".toByteArray()))) }
    val fourKHDHub by lazy { FourKHDHubProvider(ProviderInstanceId(UUID.nameUUIDFromBytes("nicetime:4khdhub".toByteArray()))) }
}

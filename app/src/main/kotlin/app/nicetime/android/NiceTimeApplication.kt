package app.nicetime.android

import android.app.Application
import androidx.media3.common.util.UnstableApi
import app.nicetime.android.core.data.CredentialStore
import app.nicetime.android.core.media.DownloadRuntime
import app.nicetime.android.core.media.DownloadSecrets

@UnstableApi
class NiceTimeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val credentials = CredentialStore(this)
        DownloadRuntime.secrets = object : DownloadSecrets {
            override fun put(id: String, value: String) = credentials.put("download:$id", value)
            override fun get(id: String) = credentials.get("download:$id")
            override fun remove(id: String) = credentials.remove("download:$id")
        }
        // Startup reconciliation: check for incomplete downloads that need recovery
        DownloadRuntime.reconcileOnStartup(this)
    }
}

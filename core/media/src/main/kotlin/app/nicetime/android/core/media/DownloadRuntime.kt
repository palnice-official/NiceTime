package app.nicetime.android.core.media

import android.app.Notification
import android.content.Context
import android.os.StatFs
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.*
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.source.MediaSource
import app.nicetime.android.core.model.ReleaseRef
import app.nicetime.android.core.model.ResolvedStream
import app.nicetime.android.core.model.downloadIdentity
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DownloadSnapshot(
    val id: String,
    val title: String,
    val state: String,
    val bytesDownloaded: Long,
    val contentLength: Long?,
    val percent: Float?,
    val stopReason: Int = 0,
    val contentType: String? = null,
)

/** Bound by the app to encrypted persistence, without a media-to-data dependency. */
interface DownloadSecrets {
    fun put(id: String, value: String)
    fun get(id: String): String?
    fun remove(id: String)
}

@UnstableApi
object DownloadRuntime {
    const val CHANNEL_ID = "nicetime_downloads"
    private const val USER_PAUSED = 1
    private const val LOW_STORAGE = 2
    private const val NETWORK_LOST = 3
    lateinit var secrets: DownloadSecrets
    @Volatile private var manager: DownloadManager? = null
    private lateinit var cache: SimpleCache
    private val executor = Executors.newFixedThreadPool(2)

    @Synchronized fun manager(context: Context): DownloadManager = manager ?: run {
        val app = context.applicationContext
        val database = StandaloneDatabaseProvider(app)
        cache = SimpleCache(app.getDir("offline_media", Context.MODE_PRIVATE), NoOpCacheEvictor(), database)
        val factory = DownloaderFactory { request ->
            val secret = secrets.get(request.id)
            val metadata = secret?.let { runCatching { JSONObject(it) }.getOrNull() }
            val headers = metadata?.optJSONObject("headers")?.let { json ->
                json.keys().asSequence().associateWith { json.getString(it) }
            }.orEmpty()
            val hosts = metadata?.optJSONArray("hosts")?.let { json ->
                (0 until json.length()).map { json.getString(it) }.toSet()
            }.orEmpty()
            val upstream = OkHttpDataSource.Factory(scopedMediaClient(request.uri.toString(), headers, hosts))
            val cacheFactory = cacheFactory(request.id).setUpstreamDataSourceFactory {
                val delegate = upstream.createDataSource()
                object : androidx.media3.datasource.DataSource by delegate {
                    private var bytesSinceStorageCheck = 0
                    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                        if (metadata == null) throw IOException("Source context unavailable; remove and download again")
                        if (!hasStorage(app)) throw IOException("Insufficient storage")
                        val length = delegate.open(dataSpec)
                        try {
                            val response = delegate.responseHeaders
                            val etag = response.entries.firstOrNull { it.key.equals("ETag", true) }?.value?.firstOrNull()
                            val key = dataSpec.uri.toString()
                            synchronized(secrets) {
                                val latest = secrets.get(request.id)?.let(::JSONObject) ?: throw IOException("Source context unavailable")
                                val versions = latest.optJSONObject("versions") ?: JSONObject()
                                val previous = versions.optString(key).takeIf { it.isNotEmpty() }
                                if (dataSpec.position > 0 && (etag == null || etag.startsWith("W/") || etag != previous)) {
                                    throw IOException("Resource identity changed or cannot be verified; remove and download again")
                                }
                                if (etag != null && !etag.startsWith("W/") && previous != etag) {
                                    if (previous != null) throw IOException("Resource identity changed; remove and download again")
                                    versions.put(key, etag)
                                    secrets.put(request.id, latest.put("versions", versions).toString())
                                }
                            }
                            return length
                        } catch (error: Exception) { delegate.close(); throw error }
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (bytesSinceStorageCheck >= 1024 * 1024) {
                            bytesSinceStorageCheck = 0
                            if (!hasStorage(app)) throw IOException("Insufficient storage")
                        }
                        return delegate.read(buffer, offset, length).also { if (it > 0) bytesSinceStorageCheck += it }
                    }
                }
            }
            DefaultDownloaderFactory(cacheFactory, executor).createDownloader(request)
        }
        DownloadManager(app, DefaultDownloadIndex(database), factory).apply {
            maxParallelDownloads = 2
            minRetryCount = 2
            requirements = networkRequirements(app.getSharedPreferences("download_policy", Context.MODE_PRIVATE).getBoolean("wifi_only", true))
            addListener(object : DownloadManager.Listener {
                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) { secrets.remove(download.request.id) }
                override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                    if (download.state == Download.STATE_FAILED && !hasStorage(app)) downloadManager.setStopReason(download.request.id, LOW_STORAGE)
                }
            })
        }.also { manager = it }
    }

    private fun cacheFactory(id: String) = CacheDataSource.Factory().setCache(cache)
        .setCacheKeyFactory { spec -> "$id|${spec.key ?: spec.uri}" }

    fun stableId(ref: ReleaseRef): String = downloadIdentity(ref)

    fun hasStorage(context: Context, minBytes: Long = 500L * 1024 * 1024): Boolean {
        val stat = StatFs(context.getDir("offline_media", Context.MODE_PRIVATE).path)
        return stat.availableBytes >= maxOf(minBytes, stat.totalBytes / 20)
    }

    /** Streaming currently has no disk cache. Never touch offline media here. */
    fun clearStreamCache(context: Context) = Unit

    private fun networkRequirements(wifiOnly: Boolean) = Requirements(Requirements.NETWORK or if (wifiOnly) Requirements.NETWORK_UNMETERED else 0)

    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        context.getSharedPreferences("download_policy", Context.MODE_PRIVATE).edit().putBoolean("wifi_only", wifiOnly).apply()
        manager(context).requirements = networkRequirements(wifiOnly)
    }

    suspend fun enqueue(context: Context, stream: ResolvedStream, title: String, wifiOnly: Boolean) {
        check(hasStorage(context)) { "Insufficient storage: free space and try again" }
        val manager = manager(context)
        val id = stableId(stream.release)
        check(manager.downloadIndex.getDownload(id) == null) { "Already queued. Remove it in Downloads before replacing its source." }
        val upstream = OkHttpDataSource.Factory(scopedMediaClient(stream.uri, stream.requestHeaders, stream.allowedHosts))
        val item = MediaItem.Builder().setUri(stream.uri).setMimeType(stream.mimeType).build()
        val helper = DownloadHelper.forMediaItem(context, item, DefaultRenderersFactory(context), upstream)
        val request = try {
            suspendCancellableCoroutine<DownloadRequest> { continuation ->
                continuation.invokeOnCancellation { helper.release() }
                helper.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper) {
                        if (continuation.isActive) continuation.resume(helper.getDownloadRequest(id, title.take(200).toByteArray(Charsets.UTF_8)))
                    }
                    override fun onPrepareError(helper: DownloadHelper, error: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(IOException("Unable to prepare offline media", error))
                    }
                })
            }
        } finally { helper.release() }
        check(manager.downloadIndex.getDownload(id) == null) { "Already queued" }
        secrets.put(id, JSONObject().put("headers", JSONObject(stream.requestHeaders)).put("hosts", JSONArray(stream.allowedHosts.toList())).toString())
        setWifiOnly(context, wifiOnly)
        DownloadService.sendAddDownload(context, NiceTimeDownloadService::class.java, request, false)
    }

    fun pause(context: Context, id: String) = DownloadService.sendSetStopReason(context, NiceTimeDownloadService::class.java, id, USER_PAUSED, false)
    fun resume(context: Context, id: String) {
        check(hasStorage(context)) { "Insufficient storage" }
        DownloadService.sendSetStopReason(context, NiceTimeDownloadService::class.java, id, Download.STOP_REASON_NONE, false)
        DownloadService.sendResumeDownloads(context, NiceTimeDownloadService::class.java, false)
    }
    fun remove(context: Context, id: String) = DownloadService.sendRemoveDownload(context, NiceTimeDownloadService::class.java, id, false)

    /** Offline-only media source. No network fallback. */
    fun offlineSource(context: Context, id: String): MediaSource {
        val download = manager(context).downloadIndex.getDownload(id)
        check(download?.state == Download.STATE_COMPLETED) { "Incomplete download" }
        return DownloadHelper.createMediaSource(download!!.request, cacheFactory(id)
            .setUpstreamDataSourceFactory(null).setCacheWriteDataSinkFactory(null))
    }

    /** Check if a download is complete and available offline. */
    fun isOfflineReady(context: Context, id: String): Boolean {
        val download = manager(context).downloadIndex.getDownload(id)
        return download?.state == Download.STATE_COMPLETED
    }

    /** Get download info for offline playback routing. */
    fun getDownloadInfo(context: Context, id: String): DownloadSnapshot? {
        val download = manager(context).downloadIndex.getDownload(id) ?: return null
        return DownloadSnapshot(
            id = download.request.id,
            title = download.request.data.toString(Charsets.UTF_8).ifBlank { "Offline video" },
            state = mapState(download),
            bytesDownloaded = download.bytesDownloaded,
            contentLength = download.contentLength.takeIf { it > 0 },
            percent = download.percentDownloaded.takeIf { it >= 0 },
            stopReason = download.stopReason,
            contentType = download.request.mimeType,
        )
    }

    fun observe(context: Context): Flow<List<DownloadSnapshot>> = callbackFlow {
        val manager = manager(context)
        fun publish() { trySend(readAll(manager)) }
        val listener = object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = publish()
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) = publish()
            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = publish()
            override fun onRequirementsStateChanged(downloadManager: DownloadManager, requirements: Requirements, notMetRequirements: Int) = publish()
        }
        manager.addListener(listener)
        val poll = launch { while (true) { publish(); delay(1000) } }
        awaitClose { poll.cancel(); manager.removeListener(listener) }
    }

    /** Startup reconciliation: check for incomplete downloads that need recovery. */
    fun reconcileOnStartup(context: Context) {
        val manager = manager(context)
        manager.currentDownloads.forEach { download ->
            when {
                download.state == Download.STATE_STOPPED && download.stopReason == USER_PAUSED -> {
                    // User paused, keep paused
                }
                download.state == Download.STATE_FAILED -> {
                    // Check if it's a storage issue
                    if (!hasStorage(context)) {
                        manager.setStopReason(download.request.id, LOW_STORAGE)
                    }
                }
                download.state == Download.STATE_QUEUED && manager.notMetRequirements != 0 -> {
                    // Waiting for network - will auto-resume when requirements met
                }
            }
        }
    }

    private fun mapState(download: Download): String = when {
        download.stopReason == LOW_STORAGE -> "Insufficient storage"
        download.stopReason == USER_PAUSED -> "Paused"
        download.stopReason == NETWORK_LOST -> "Waiting for network"
        download.state == Download.STATE_QUEUED && download.stopReason != 0 -> "Paused"
        download.state == Download.STATE_QUEUED -> "Queued"
        download.state == Download.STATE_STOPPED -> "Paused"
        download.state == Download.STATE_DOWNLOADING -> "Downloading"
        download.state == Download.STATE_COMPLETED -> "Offline ready"
        download.state == Download.STATE_FAILED -> "Failed: refresh source"
        download.state == Download.STATE_REMOVING -> "Deleting"
        else -> "Restarting"
    }

    private fun readAll(manager: DownloadManager): List<DownloadSnapshot> {
        val active = manager.currentDownloads.associateBy { it.request.id }
        return buildList {
            manager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val item = active[cursor.download.request.id] ?: cursor.download
                    add(DownloadSnapshot(item.request.id, item.request.data.toString(Charsets.UTF_8).ifBlank { "Offline video" },
                        mapState(item), item.bytesDownloaded, item.contentLength.takeIf { it > 0 },
                        item.percentDownloaded.takeIf { it >= 0 }, item.stopReason, item.request.mimeType))
                }
            }
        }
    }
}

@UnstableApi
class NiceTimeDownloadService : DownloadService(3102, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadRuntime.CHANNEL_ID, R.string.download_channel_name, 0) {
    private lateinit var notifications: DownloadNotificationHelper
    override fun onCreate() { notifications = DownloadNotificationHelper(this, DownloadRuntime.CHANNEL_ID); super.onCreate() }
    override fun getDownloadManager(): DownloadManager = DownloadRuntime.manager(this)
    override fun getScheduler(): Scheduler? = null
    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        notifications.buildProgressNotification(this, android.R.drawable.stat_sys_download, null, null, downloads, notMetRequirements)
}

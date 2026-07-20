package app.vimusic.android.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.workmanager.WorkManagerScheduler
import app.vimusic.android.Database
import app.vimusic.android.R
import app.vimusic.android.appContainer
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.utils.ActionReceiver
import app.vimusic.android.utils.download
import app.vimusic.android.utils.safeUnregisterReceiver
import app.vimusic.android.utils.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

// While the class is not a singleton (lifecycle), there should only be one download state at a time
private val mutableDownloadState = MutableStateFlow(false)
val downloadState = mutableDownloadState.asStateFlow()

private const val DOWNLOAD_NOTIFICATION_UPDATE_INTERVAL = 1000L // default
private const val DOWNLOAD_WORK_NAME = "precacher-work"

/**
 * DownloadService keeps the first DownloadManager returned by a service class and reuses it after
 * that service instance is destroyed. Its executor and listeners therefore need process lifetime,
 * not PrecacheService lifetime.
 */
private val precacheScope = CoroutineScope(
    Dispatchers.IO + SupervisorJob() + CoroutineName("Precache-Process-Scope")
)
private val precacheExecutor = Executor { command -> precacheScope.launch { command.run() } }
private val downloadManagerLock = Any()

@Volatile
private var processDownloadManager: DownloadManager? = null

private val downloadListener = object : DownloadManager.Listener {
    override fun onInitialized(downloadManager: DownloadManager) {
        mutableDownloadState.value = !downloadManager.isIdle
    }

    override fun onIdle(downloadManager: DownloadManager) {
        mutableDownloadState.value = false
    }

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?,
    ) {
        mutableDownloadState.value = !downloadManager.isIdle

        if (download.state == Download.STATE_COMPLETED && download.contentLength > 0L) {
            val songId = download.request.id
            val contentLength = download.contentLength

            precacheScope.launch {
                runCatching {
                    Database.upsertFormatContentLength(songId, contentLength)
                }.onFailure(Throwable::printStackTrace)
            }
        }
    }

    override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
        mutableDownloadState.value = !downloadManager.isIdle
    }
}

private fun processDownloadManager(context: Context): DownloadManager =
    processDownloadManager ?: synchronized(downloadManagerLock) {
        processDownloadManager ?: run {
            val applicationContext = context.applicationContext
            val cache = PlayerService.sharedCache(applicationContext)

            DownloadManager(
                /* context = */ applicationContext,
                /* databaseProvider = */ PlayerService.createDatabaseProvider(applicationContext),
                /* cache = */ cache,
                /* upstreamFactory = */ NewPipeAudioMediaSourceFactory.createDataSourceFactory(
                    context = applicationContext,
                    cache = cache,
                    resolveDashManifestUri = NewPipeAudioMediaSourceFactory::resolveDashManifestUri,
                ),
                /* executor = */ precacheExecutor,
            ).apply {
                maxParallelDownloads = 3
                minRetryCount = 1
                requirements = Requirements(Requirements.NETWORK)
                addListener(downloadListener)
            }.also { processDownloadManager = it }
        }
    }

@OptIn(UnstableApi::class)
class PrecacheService : DownloadService(
    /* foregroundNotificationId             = */ ServiceNotifications.download.notificationId!!,
    /* foregroundNotificationUpdateInterval = */ DOWNLOAD_NOTIFICATION_UPDATE_INTERVAL,
    /* channelId                            = */ ServiceNotifications.download.id,
    /* channelNameResourceId                = */ R.string.pre_cache,
    /* channelDescriptionResourceId         = */ 0
) {
    private val downloadNotificationHelper by lazy {
        DownloadNotificationHelper(
            /* context = */ this,
            /* channelId = */ ServiceNotifications.download.id
        )
    }

    private val notificationActionReceiver = NotificationActionReceiver()

    inner class NotificationActionReceiver : ActionReceiver("app.vimusic.android.precache") {
        val cancel by action { context, _ ->
            runCatching {
                sendPauseDownloads(
                    /* context         = */ context,
                    /* clazz           = */ PrecacheService::class.java,
                    /* foreground      = */ true
                )
            }.recoverCatching {
                sendPauseDownloads(
                    /* context         = */ context,
                    /* clazz           = */ PrecacheService::class.java,
                    /* foreground      = */ false
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        notificationActionReceiver.register()
    }

    override fun getDownloadManager(): DownloadManager = processDownloadManager(this)

    override fun getScheduler() = WorkManagerScheduler(this, DOWNLOAD_WORK_NAME)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ) = NotificationCompat
        .Builder(
            /* context = */ this,
            /* notification = */ downloadNotificationHelper.buildProgressNotification(
                /* context            = */ this,
                /* smallIcon          = */ R.drawable.download,
                /* contentIntent      = */ null,
                /* message            = */ null,
                /* downloads          = */ downloads,
                /* notMetRequirements = */ notMetRequirements
            )
        )
        .setChannelId(ServiceNotifications.download.id)
        .addAction(
            NotificationCompat.Action.Builder(
                /* icon = */ R.drawable.close,
                /* title = */ getString(R.string.cancel),
                /* intent = */ notificationActionReceiver.cancel.pendingIntent
            ).build()
        )
        .build()

    override fun onDestroy() {
        super.onDestroy()

        safeUnregisterReceiver(notificationActionReceiver)
    }

    companion object {
        fun scheduleCache(context: Context, mediaItems: Iterable<MediaItem>) {
            mediaItems.distinctBy(MediaItem::mediaId).forEach { scheduleCache(context, it) }
        }

        fun scheduleCache(context: Context, mediaItem: MediaItem) {
            if (mediaItem.isLocal) return
            val repository = context.appContainer.precacheRepository
            if (DataPreferences.cacheFavoritesOnly) {
                val isFavorite = repository.isFavorite(mediaItem.mediaId)
                if (!isFavorite) return
            }

            val downloadRequest = DownloadRequest
                .Builder(
                    /* id      = */ mediaItem.mediaId,
                    /* uri     = */ mediaItem.mediaId.toUri()
                )
                .setCustomCacheKey(mediaItem.mediaId)
                .setData(mediaItem.mediaId.encodeToByteArray())
                .build()

            repository.insertMediaItem(mediaItem).onFailure { return }

            context.download<PrecacheService>(downloadRequest).exceptionOrNull()?.let {
                if (it is CancellationException) throw it

                it.printStackTrace()
                context.toast(context.getString(R.string.error_pre_cache))
            }
        }
    }
}

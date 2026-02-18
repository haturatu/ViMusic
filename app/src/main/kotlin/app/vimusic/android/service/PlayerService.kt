package app.vimusic.android.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import app.vimusic.android.MainActivity
import app.vimusic.android.R
import app.vimusic.android.appContainer
import app.vimusic.android.extractor.NewPipeExtractorClient
import app.vimusic.android.models.Event
import app.vimusic.android.models.Format
import app.vimusic.android.models.QueuedMediaItem
import app.vimusic.android.models.Song
import app.vimusic.android.models.SongWithContentLength
import app.vimusic.android.preferences.AppearancePreferences
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.preferences.PlayerPreferences
import app.vimusic.android.utils.ActionReceiver
import app.vimusic.android.utils.ConditionalCacheDataSourceFactory
import app.vimusic.android.utils.GlyphInterface
import app.vimusic.android.utils.InvincibleService
import app.vimusic.android.utils.TimerJob
import app.vimusic.android.utils.YouTubeRadio
import app.vimusic.android.utils.activityPendingIntent
import app.vimusic.android.utils.asDataSource
import app.vimusic.android.utils.broadcastPendingIntent
import app.vimusic.android.utils.defaultDataSource
import app.vimusic.android.utils.enqueue
import app.vimusic.android.utils.findCause
import app.vimusic.android.utils.findNextMediaItemById
import app.vimusic.android.utils.forcePlayFromBeginning
import app.vimusic.android.utils.forceSeekToNext
import app.vimusic.android.utils.forceSeekToPrevious
import app.vimusic.android.utils.get
import app.vimusic.android.utils.handleRangeErrors
import app.vimusic.android.utils.handleUnknownErrors
import app.vimusic.android.utils.InvalidPlaybackResponseException
import app.vimusic.android.utils.intent
import app.vimusic.android.utils.mediaItems
import app.vimusic.android.utils.PlaybackRetryManager
import app.vimusic.android.utils.progress
import app.vimusic.android.utils.readOnlyWhen
import app.vimusic.android.utils.safeUnregisterReceiver
import app.vimusic.android.utils.setPlaybackPitch
import app.vimusic.android.utils.shouldBePlaying
import app.vimusic.android.utils.thumbnail
import app.vimusic.android.utils.timer
import app.vimusic.android.utils.toast
import app.vimusic.android.utils.withFreshConnectionHeaders
import app.vimusic.compose.preferences.SharedPreferencesProperty
import app.vimusic.core.data.enums.ExoPlayerDiskCacheSize
import app.vimusic.core.data.utils.UriCache
import app.vimusic.core.ui.utils.EqualizerIntentBundleAccessor
import app.vimusic.core.ui.utils.isAtLeastAndroid10
import app.vimusic.core.ui.utils.isAtLeastAndroid6
import app.vimusic.core.ui.utils.isAtLeastAndroid8
import app.vimusic.core.ui.utils.isAtLeastAndroid9
import app.vimusic.core.ui.utils.songBundle
import app.vimusic.core.ui.utils.streamVolumeFlow
import app.vimusic.providers.innertube.models.NavigationEndpoint
import app.vimusic.providers.sponsorblock.models.Action
import app.vimusic.providers.sponsorblock.models.Category
import app.vimusic.providers.sponsorblock.models.Segment
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import android.os.Binder as AndroidBinder

const val LOCAL_KEY_PREFIX = "local:"
private const val TAG = "PlayerService"

@get:OptIn(UnstableApi::class)
val DataSpec.isLocal get() = key?.startsWith(LOCAL_KEY_PREFIX) == true

val MediaItem.isLocal get() = mediaId.startsWith(LOCAL_KEY_PREFIX)
val Song.isLocal get() = id.startsWith(LOCAL_KEY_PREFIX)

@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass", "TooManyFunctions") // intended in this class: it is a service
@OptIn(UnstableApi::class)
class PlayerService : InvincibleService(), Player.Listener, PlaybackStatsListener.Callback {
    private val playerRepository by lazy { applicationContext.appContainer.playerRepository }
    private val searchResultRepository by lazy { applicationContext.appContainer.searchResultRepository }
    private val sponsorBlockRepository by lazy { applicationContext.appContainer.sponsorBlockRepository }
    private lateinit var mediaSession: MediaSession
    private lateinit var cache: SimpleCache
    private lateinit var player: ExoPlayer
    private val uriCache = UriCache<String, Long?>(size = 64)

    private var timerJob: TimerJob? by mutableStateOf(null)
    private var radio: YouTubeRadio? = null
    private val playbackRetryManager = PlaybackRetryManager(longArrayOf(500L, 1500L, 3000L))

    private lateinit var bitmapProvider: BitmapProvider

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var preferenceUpdaterJob: Job? = null
    private var volumeNormalizationJob: Job? = null
    private var sponsorBlockJob: Job? = null
    private var proactiveRefreshJob: Job? = null

    override var isInvincibilityEnabled by mutableStateOf(false)

    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var reverb: PresetReverb? = null

    private val binder = Binder()

    private var isNotificationStarted = false
    override val notificationId get() = ServiceNotifications.default.notificationId!!
    private val notificationActionReceiver = NotificationActionReceiver()

    private val mediaItemState = MutableStateFlow<MediaItem?>(null)
    private val isLikedState = mediaItemState
        .flatMapMerge { item ->
            item?.mediaId?.let {
                playerRepository
                    .observeLikedAt(it)
                    .distinctUntilChanged()
                    .cancellable()
            } ?: flowOf(null)
        }
        .map { it != null }
        .onEach {
            updateNotification()
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private val glyphInterface by lazy { GlyphInterface(applicationContext) }

    private var poiTimestamp: Long? by mutableStateOf(null)
    private var proactiveRefreshInFlight = false
    private var lastProactiveRefreshMediaId: String? = null
    private var lastProactiveRefreshAtMs: Long = 0L

    override fun onBind(intent: Intent?): AndroidBinder {
        super.onBind(intent)
        return binder
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onCreate() {
        super.onCreate()

        glyphInterface.tryInit()

        bitmapProvider = BitmapProvider(
            context = this,
            getBitmapSize = {
                (512 * resources.displayMetrics.density)
                    .roundToInt()
                    .coerceAtMost(AppearancePreferences.maxThumbnailSize)
            },
            getColor = { isSystemInDarkMode ->
                if (isSystemInDarkMode) Color.BLACK else Color.WHITE
            }
        )

        cache = createCache(this)
        player = ExoPlayer.Builder(this, createRendersFactory(), createMediaSourceFactory())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                /* audioAttributes = */ AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ PlayerPreferences.handleAudioFocus
            )
            .setUsePlatformDiagnostics(false)
            .build()
            .apply {
                skipSilenceEnabled = PlayerPreferences.skipSilence
                addListener(this@PlayerService)
                addAnalyticsListener(
                    PlaybackStatsListener(
                        /* keepHistory = */ false,
                        /* callback = */ this@PlayerService
                    )
                )
            }

        updateRepeatMode()
        maybeRestorePlayerQueue()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(activityPendingIntent<MainActivity>())
            .build()

        coroutineScope.launch {
            var first = true
            combine(mediaItemState, isLikedState) { mediaItem, _ ->
                // work around NPE in other processes
                if (first) {
                    first = false
                    return@combine
                }

                if (mediaItem == null) return@combine
                withContext(Dispatchers.Main) {
                    updateNotification()
                }
            }.collect()
        }

        notificationActionReceiver.register()
        maybeResumePlaybackWhenDeviceConnected()
        startProactiveRefreshLoop()

        preferenceUpdaterJob = coroutineScope.launch {
            fun <T : Any> subscribe(
                prop: SharedPreferencesProperty<T>,
                callback: (T) -> Unit
            ) = launch { prop.stateFlow.collectLatest { handler.post { callback(it) } } }

            subscribe(AppearancePreferences.isShowingThumbnailInLockscreenProperty) {
                maybeShowSongCoverInLockScreen()
            }

            subscribe(PlayerPreferences.bassBoostLevelProperty) { maybeBassBoost() }
            subscribe(PlayerPreferences.bassBoostProperty) { maybeBassBoost() }
            subscribe(PlayerPreferences.reverbProperty) { maybeReverb() }
            subscribe(PlayerPreferences.isInvincibilityEnabledProperty) {
                this@PlayerService.isInvincibilityEnabled = it
            }
            subscribe(PlayerPreferences.pitchProperty) {
                player.setPlaybackPitch(it.coerceAtLeast(0.01f))
            }
            subscribe(PlayerPreferences.queueLoopEnabledProperty) { updateRepeatMode() }
            subscribe(PlayerPreferences.resumePlaybackWhenDeviceConnectedProperty) {
                maybeResumePlaybackWhenDeviceConnected()
            }
            subscribe(PlayerPreferences.skipSilenceProperty) { player.skipSilenceEnabled = it }
            subscribe(PlayerPreferences.speedProperty) {
                player.setPlaybackSpeed(it.coerceAtLeast(0.01f))
            }
            subscribe(PlayerPreferences.trackLoopEnabledProperty) { updateRepeatMode() }
            subscribe(PlayerPreferences.volumeNormalizationBaseGainProperty) { maybeNormalizeVolume() }
            subscribe(PlayerPreferences.volumeNormalizationProperty) { maybeNormalizeVolume() }
            subscribe(PlayerPreferences.sponsorBlockEnabledProperty) { maybeSponsorBlock() }

            launch {
                val audioManager = getSystemService<AudioManager>()
                val stream = AudioManager.STREAM_MUSIC

                val min = when {
                    audioManager == null -> 0
                    isAtLeastAndroid9 -> audioManager.getStreamMinVolume(stream)

                    else -> 0
                }

                streamVolumeFlow(stream).collectLatest {
                    handler.post { if (it == min) player.pause() }
                }
            }
        }
    }

    private fun updateRepeatMode() {
        player.repeatMode = when {
            PlayerPreferences.trackLoopEnabled -> Player.REPEAT_MODE_ONE
            PlayerPreferences.queueLoopEnabled -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.shouldBePlaying || PlayerPreferences.stopWhenClosed)
            broadcastPendingIntent<NotificationDismissReceiver>().send()
        super.onTaskRemoved(rootIntent)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
        maybeSavePlayerQueue()

    override fun onDestroy() {
        runCatching {
            maybeSavePlayerQueue()

            player.removeListener(this)
            player.stop()
            player.release()

            safeUnregisterReceiver(notificationActionReceiver)

            mediaSession.release()
            cache.release()

            loudnessEnhancer?.release()
            preferenceUpdaterJob?.cancel()
            proactiveRefreshJob?.cancel()

            coroutineScope.cancel()
            glyphInterface.close()
        }

        super.onDestroy()
    }

    override fun shouldBeInvincible() = !player.shouldBePlaying

    override fun onConfigurationChanged(newConfig: Configuration) {
        handler.post {
            if (!bitmapProvider.setDefaultBitmap() || player.currentMediaItem == null) return@post
            updateNotification()
        }

        super.onConfigurationChanged(newConfig)
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        val totalPlayTimeMs = playbackStats.totalPlayTimeMs
        if (totalPlayTimeMs < 5000) return

        val mediaItem = eventTime.timeline[eventTime.windowIndex].mediaItem
        runCatching { playerRepository.insertSong(mediaItem) }

        if (!DataPreferences.pausePlaytime) {
            runCatching {
                playerRepository.incrementTotalPlayTimeMs(mediaItem.mediaId, totalPlayTimeMs)
            }
        }

        if (!DataPreferences.pauseHistory) {
            runCatching {
                playerRepository.insertEvent(
                    Event(
                        songId = mediaItem.mediaId,
                        timestamp = System.currentTimeMillis(),
                        playTime = totalPlayTimeMs
                    )
                )
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (
            AppearancePreferences.hideExplicit &&
            mediaItem?.mediaMetadata?.extras?.songBundle?.explicit == true
        ) {
            player.forceSeekToNext()
            return
        }

        mediaItem?.mediaId?.let {
            playbackRetryManager.reset(it)
        }

        mediaItemState.update { mediaItem }

        maybeRecoverPlaybackError()
        maybeNormalizeVolume()
        maybeProcessRadio()

        with(bitmapProvider) {
            when {
                mediaItem == null -> load(null)
                mediaItem.mediaMetadata.artworkUri == lastUri -> bitmapProvider.load(lastUri)
            }
        }

        // Media3 session state is derived from the player.
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        maybeSavePlayerQueue()
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        player.currentMediaItem?.mediaId?.let { mediaId ->
            if (error.findCause<InvalidPlaybackResponseException>() != null) {
                playbackRetryManager.prepareRetry(mediaId) { uriCache.clear() }
                player.prepare()
                player.play()
                return
            }
        }

        if (
            error.findCause<InvalidResponseCodeException>()?.responseCode == 416
        ) {
            player.prepare()
            player.play()
            return
        }

        player.currentMediaItem?.mediaId?.let { mediaId ->
            val retryDelayMs = playbackRetryManager.nextRetryDelayOrNull(mediaId, error)
            if (retryDelayMs != null) {
                handler.postDelayed(
                    {
                        if (player.currentMediaItem?.mediaId != mediaId) return@postDelayed

                        playbackRetryManager.prepareRetry(mediaId) { uriCache.clear() }

                        player.prepare()
                        player.play()
                    },
                    retryDelayMs
                )
                return
            }
        }

        if (!PlayerPreferences.skipOnError || !player.hasNextMediaItem()) return

        val prev = player.currentMediaItem ?: return
        player.seekToNextMediaItem()

        ServiceNotifications.autoSkip.sendNotification(this) {
            this
                .setSmallIcon(R.drawable.app_icon)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setOnlyAlertOnce(false)
                .setContentIntent(activityPendingIntent<MainActivity>())
                .setContentText(
                    prev.mediaMetadata.title?.let {
                        getString(R.string.skip_on_error_notification, it)
                    } ?: getString(R.string.skip_on_error_notification_unknown_song)
                )
                .setContentTitle(getString(R.string.skip_on_error))
        }
    }

    private fun maybeRecoverPlaybackError() {
        if (player.playerError != null) player.prepare()
    }

    private fun startProactiveRefreshLoop() {
        proactiveRefreshJob?.cancel()
        proactiveRefreshJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(PROACTIVE_REFRESH_POLL_INTERVAL_MS)
                maybeProactivelyRefreshCurrentStream()
            }
        }
    }

    private suspend fun maybeProactivelyRefreshCurrentStream() {
        if (proactiveRefreshInFlight) return

        val mediaId = withContext(Dispatchers.Main) {
            if (!player.isPlaying) return@withContext null
            val mediaItem = player.currentMediaItem ?: return@withContext null
            if (mediaItem.isLocal) return@withContext null

            val bufferedAheadMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
            if (bufferedAheadMs > PROACTIVE_REFRESH_THRESHOLD_MS) return@withContext null

            extractYouTubeVideoId(mediaItem.mediaId)
        } ?: return

        val now = System.currentTimeMillis()
        if (
            lastProactiveRefreshMediaId == mediaId &&
            (now - lastProactiveRefreshAtMs) < PROACTIVE_REFRESH_MIN_INTERVAL_MS
        ) return

        proactiveRefreshInFlight = true
        lastProactiveRefreshMediaId = mediaId
        lastProactiveRefreshAtMs = now

        runCatching {
            val result = NewPipeExtractorClient.resolveAudioStream(mediaId)
            val streamInfo = result.streamInfo
            if (streamInfo.id != mediaId) return@runCatching

            val audioStream = result.audioStream
            if (!audioStream.isUrl) return@runCatching

            val url = audioStream.content
            if (!preflightAudioStream(url)) return@runCatching

            uriCache.push(
                key = mediaId,
                meta = null,
                uri = url.toUri(),
                validUntil = null
            )
            playbackRetryManager.markForceFreshResolve(mediaId)
        }.onFailure {
            Log.w(TAG, "Proactive refresh failed for $mediaId", it)
        }

        proactiveRefreshInFlight = false
    }

    private fun preflightAudioStream(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-1")
            .header("User-Agent", PREVIEW_USER_AGENT)
            .build()

        preflightHttpClient.newCall(request).execute().use { response ->
            val code = response.code
            val contentRange = response.header("Content-Range")
            val contentLength = response.header("Content-Length")?.toLongOrNull()

            when (code) {
                206 -> !contentRange.isNullOrBlank()
                200 -> contentLength == null || contentLength > 0L
                else -> false
            }
        }
    }.getOrDefault(false)

    private fun maybeProcessRadio() {
        if (player.mediaItemCount - player.currentMediaItemIndex > 3) return

        radio?.let { radio ->
            coroutineScope.launch(Dispatchers.Main) {
                player.addMediaItems(radio.process())
            }
        }
    }

    private fun maybeSavePlayerQueue() {
        if (!PlayerPreferences.persistentQueue) return

        val mediaItems = player.currentTimeline.mediaItems
        val mediaItemIndex = player.currentMediaItemIndex
        val mediaItemPosition = player.currentPosition

        runCatching {
            playerRepository.saveQueue(
                mediaItems.mapIndexed { index, mediaItem ->
                    QueuedMediaItem(
                        mediaItem = mediaItem,
                        position = if (index == mediaItemIndex) mediaItemPosition else null
                    )
                }
            )
        }
    }

    private fun maybeRestorePlayerQueue() {
        if (!PlayerPreferences.persistentQueue) return

        coroutineScope.launch {
            val queue = withContext(Dispatchers.IO) {
                playerRepository.loadQueue().also {
                    if (it.isNotEmpty()) playerRepository.clearQueue()
                }
            }
            if (queue.isEmpty()) return@launch

            val index = queue
                .indexOfFirst { it.position != null }
                .coerceAtLeast(0)

            withContext(Dispatchers.Main) {
                runCatching {
                    player.setMediaItems(
                        /* mediaItems = */ queue.map { item ->
                            item.mediaItem.buildUpon()
                                .setUri(item.mediaItem.mediaId)
                                .setCustomCacheKey(item.mediaItem.mediaId)
                                .build()
                                .apply {
                                    mediaMetadata.extras?.songBundle?.apply {
                                        isFromPersistentQueue = true
                                    }
                                }
                        },
                        /* startIndex = */ index,
                        /* startPositionMs = */ queue[index].position ?: C.TIME_UNSET
                    )
                    player.prepare()

                    isNotificationStarted = true
                    startForegroundService(this@PlayerService, intent<PlayerService>())
                    startForeground()
                }
            }
        }
    }

    private fun maybeNormalizeVolume() {
        if (!PlayerPreferences.volumeNormalization) {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            volumeNormalizationJob?.cancel()
            volumeNormalizationJob?.invokeOnCompletion { volumeNormalizationJob = null }
            player.volume = 1f
            return
        }

        runCatching {
            if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
        }.onFailure { return }

        val songId = player.currentMediaItem?.mediaId ?: return
        volumeNormalizationJob?.cancel()
        volumeNormalizationJob = coroutineScope.launch {
            runCatching {
                fun Float?.toMb() = ((this ?: 0f) * 100).toInt()

                playerRepository.observeLoudnessDb(songId).cancellable().collectLatest { loudness ->
                    val loudnessMb = loudness.toMb().let {
                        if (it !in -2000..2000) {
                            withContext(Dispatchers.Main) {
                                toast(
                                    getString(
                                        R.string.loudness_normalization_extreme,
                                        getString(R.string.format_db, (it / 100f).toString())
                                    )
                                )
                            }

                            0
                        } else it
                    }

                    playerRepository.observeLoudnessBoost(songId).cancellable().collectLatest { boost ->
                        withContext(Dispatchers.Main) {
                            loudnessEnhancer?.setTargetGain(
                                PlayerPreferences.volumeNormalizationBaseGain.toMb() + boost.toMb() - loudnessMb
                            )
                            loudnessEnhancer?.enabled = true
                        }
                    }
                }
            }
        }
    }

    private fun maybeSponsorBlock() {
        poiTimestamp = null

        if (!PlayerPreferences.sponsorBlockEnabled) {
            sponsorBlockJob?.cancel()
            sponsorBlockJob?.invokeOnCompletion { sponsorBlockJob = null }
            return
        }

        sponsorBlockJob?.cancel()
        sponsorBlockJob = coroutineScope.launch {
            mediaItemState.onStart { emit(mediaItemState.value) }.collectLatest { mediaItem ->
                poiTimestamp = null
                val videoId = extractYoutubeVideoId(mediaItem) ?: return@collectLatest

                sponsorBlockRepository
                    .fetchSegments(videoId)
                    ?.onSuccess { segments ->
                        updatePoiTimestamp(segments)
                        val skipSegments = segments
                            .sortedBy { it.start.inWholeMilliseconds }
                            .filter { it.action == Action.Skip }

                        runSponsorBlockSkipLoop(skipSegments)
                    }
                    ?.onFailure { it.printStackTrace() }
            }
        }
    }

    private fun extractYoutubeVideoId(mediaItem: MediaItem?): String? =
        mediaItem
            ?.mediaId
            ?.removePrefix("https://youtube.com/watch?v=")
            ?.takeIf { it.isNotBlank() }

    private fun updatePoiTimestamp(segments: List<Segment>) {
        poiTimestamp = segments
            .find { it.category == Category.PoiHighlight }
            ?.start
            ?.inWholeMilliseconds
    }

    private suspend fun runSponsorBlockSkipLoop(segments: List<Segment>) {
        if (segments.isEmpty()) return

        suspend fun posMillis() = withContext(Dispatchers.Main) { player.currentPosition }
        suspend fun speed() = withContext(Dispatchers.Main) { player.playbackParameters.speed }
        suspend fun seek(millis: Long) = withContext(Dispatchers.Main) { player.seekTo(millis) }

        val ctx = currentCoroutineContext()
        val lastSegmentEnd = segments.lastOrNull()?.end?.inWholeMilliseconds ?: return

        @Suppress("LoopWithTooManyJumpStatements")
        do {
            if (lastSegmentEnd < posMillis()) {
                yield()
                continue
            }

            val nextSegment = segments.firstOrNull { posMillis() < it.end.inWholeMilliseconds } ?: continue

            // Wait for next segment
            if (nextSegment.start.inWholeMilliseconds > posMillis()) delay(
                ((nextSegment.start.inWholeMilliseconds - posMillis()) / speed().toDouble()).milliseconds
            )

            if (posMillis().milliseconds !in nextSegment.start..nextSegment.end) {
                // Player is not in the segment for some reason, maybe the user seeked in the meantime
                yield()
                continue
            }

            seek(nextSegment.end.inWholeMilliseconds)
        } while (ctx.isActive)
    }

    private fun maybeBassBoost() {
        if (!PlayerPreferences.bassBoost) {
            runCatching {
                bassBoost?.enabled = false
                bassBoost?.release()
            }
            bassBoost = null
            maybeNormalizeVolume()
            return
        }

        runCatching {
            if (bassBoost == null) bassBoost = BassBoost(0, player.audioSessionId)
            bassBoost?.setStrength(PlayerPreferences.bassBoostLevel.toShort())
            bassBoost?.enabled = true
        }.onFailure {
            toast(getString(R.string.error_bassboost_init))
        }
    }

    private fun maybeReverb() {
        if (PlayerPreferences.reverb == PlayerPreferences.Reverb.None) {
            runCatching {
                reverb?.enabled = false
                player.clearAuxEffectInfo()
                reverb?.release()
            }
            reverb = null
            return
        }

        runCatching {
            if (reverb == null) reverb = PresetReverb(1, player.audioSessionId)
            reverb?.preset = PlayerPreferences.reverb.preset
            reverb?.enabled = true
            reverb?.id?.let { player.setAuxEffectInfo(AuxEffectInfo(it, 1f)) }
        }
    }

    private fun maybeShowSongCoverInLockScreen() = Unit

    private fun maybeResumePlaybackWhenDeviceConnected() {
        if (!isAtLeastAndroid6) return

        if (!PlayerPreferences.resumePlaybackWhenDeviceConnected) {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallback = null
            return
        }
        if (audioManager == null) audioManager = getSystemService<AudioManager>()

        audioDeviceCallback =
            @SuppressLint("NewApi")
            object : AudioDeviceCallback() {
                private fun canPlayMusic(audioDeviceInfo: AudioDeviceInfo) =
                    audioDeviceInfo.isSink && (
                            audioDeviceInfo.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                    audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                    audioDeviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                            )
                        .let {
                            if (!isAtLeastAndroid8) it else
                                it || audioDeviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET
                        }

                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    if (!player.isPlaying && addedDevices.any(::canPlayMusic)) player.play()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = Unit
            }

        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, handler)
    }

    private fun sendOpenEqualizerIntent() = sendBroadcast(
        Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            replaceExtras(
                EqualizerIntentBundleAccessor.bundle {
                    audioSession = player.audioSessionId
                    packageName = packageName
                    contentType = AudioEffect.CONTENT_TYPE_MUSIC
                }
            )
        }
    )

    private fun sendCloseEqualizerIntent() = sendBroadcast(
        Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
            replaceExtras(
                EqualizerIntentBundleAccessor.bundle {
                    audioSession = player.audioSessionId
                }
            )
        }
    )

    // legacy behavior may cause inconsistencies, but not available on sdk 24 or lower
    @Suppress("DEPRECATION")
    override fun onEvents(player: Player, events: Player.Events) {
        if (
            !events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_IS_LOADING_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED
            )
        ) return

        val notification = notification()

        if (notification == null) {
            isNotificationStarted = false
            makeInvincible(false)
            stopForeground(false)
            sendCloseEqualizerIntent()
            ServiceNotifications.default.cancel(this)
            return
        }

        if (player.shouldBePlaying && !isNotificationStarted) {
            isNotificationStarted = true
            startForegroundService(this@PlayerService, intent<PlayerService>())
            startForeground()
            makeInvincible(false)
            sendOpenEqualizerIntent()
        } else {
            if (!player.shouldBePlaying) {
                isNotificationStarted = false
                stopForeground(false)
                makeInvincible(true)
                sendCloseEqualizerIntent()
            }
            updateNotification()
        }
    }

    private fun notification(): (NotificationCompat.Builder.() -> NotificationCompat.Builder)? {
        if (player.currentMediaItem == null) return null

        val mediaMetadata = player.mediaMetadata

        bitmapProvider.load(mediaMetadata.artworkUri) {
            maybeShowSongCoverInLockScreen()
            updateNotification()
        }

        return {
            this
                .setContentTitle(mediaMetadata.title?.toString().orEmpty())
                .setContentText(mediaMetadata.artist?.toString().orEmpty())
                .setSubText(player.playerError?.message)
                .setLargeIcon(bitmapProvider.bitmap)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSmallIcon(
                    player.playerError?.let { R.drawable.alert_circle } ?: R.drawable.app_icon
                )
                .setOngoing(false)
                .setContentIntent(
                    activityPendingIntent<MainActivity>(flags = PendingIntent.FLAG_UPDATE_CURRENT)
                )
                .setDeleteIntent(broadcastPendingIntent<NotificationDismissReceiver>())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setStyle(
                    MediaStyleNotificationHelper.MediaStyle(mediaSession)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .addAction(
                    R.drawable.play_skip_back,
                    getString(R.string.skip_back),
                    notificationActionReceiver.previous.pendingIntent
                )
                .let {
                    if (player.shouldBePlaying) it.addAction(
                        R.drawable.pause,
                        getString(R.string.pause),
                        notificationActionReceiver.pause.pendingIntent
                    )
                    else it.addAction(
                        R.drawable.play,
                        getString(R.string.play),
                        notificationActionReceiver.play.pendingIntent
                    )
                }
                .addAction(
                    R.drawable.play_skip_forward,
                    getString(R.string.skip_forward),
                    notificationActionReceiver.next.pendingIntent
                )
                .addAction(
                    if (isLikedState.value) R.drawable.heart else R.drawable.heart_outline,
                    getString(R.string.like),
                    notificationActionReceiver.like.pendingIntent
                )
        }
    }

    private fun updateNotification() = runCatching {
        handler.post {
            notification()?.let { ServiceNotifications.default.sendNotification(this, it) }
        }
    }

    override fun startForeground() {
        notification()
            ?.let { ServiceNotifications.default.startForeground(this, it) }
    }

    private fun createMediaSourceFactory() = DefaultMediaSourceFactory(
        /* dataSourceFactory = */ createYouTubeDataSourceResolverFactory(
            findMediaItem = { videoId ->
                withContext(Dispatchers.Main) {
                    player.findNextMediaItemById(videoId)
                }
            },
            context = applicationContext,
            cache = cache,
            uriCache = uriCache,
            dbWriteScope = coroutineScope,
            playbackRetryManager = playbackRetryManager
        ),
        /* extractorsFactory = */ DefaultExtractorsFactory()
    ).setLoadErrorHandlingPolicy(
        object : DefaultLoadErrorHandlingPolicy() {
            override fun isEligibleForFallback(exception: IOException) = true
        }
    )

    private fun createRendersFactory() = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            val minimumSilenceDuration =
                PlayerPreferences.minimumSilence.coerceIn(1000L..2_000_000L)

            return DefaultAudioSink.Builder(applicationContext)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioProcessorChain(
                        arrayOf(),
                        SilenceSkippingAudioProcessor(
                            /* minimumSilenceDurationUs = */ minimumSilenceDuration,
                            /* silenceRetentionRatio = */ 0.01f,
                            /* maxSilenceToKeepDurationUs = */ minimumSilenceDuration,
                            /* minVolumeToKeepPercentageWhenMuting = */ 0,
                            /* silenceThresholdLevel = */ 256
                        ),
                        SonicAudioProcessor()
                    )
                )
                .build()
                .apply {
                    if (isAtLeastAndroid10) setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED)
                }
        }
    }

    @Stable
    inner class Binder : AndroidBinder() {
        val player: ExoPlayer
            get() = this@PlayerService.player

        val cache: Cache
            get() = this@PlayerService.cache

        val mediaSession
            get() = this@PlayerService.mediaSession

        val sleepTimerMillisLeft: StateFlow<Long?>?
            get() = timerJob?.millisLeft

        private var radioJob: Job? = null

        var isLoadingRadio by mutableStateOf(false)
            private set

        var invincible
            get() = isInvincibilityEnabled
            set(value) {
                isInvincibilityEnabled = value
            }

        val poiTimestamp get() = this@PlayerService.poiTimestamp

        fun setBitmapListener(listener: ((Bitmap?) -> Unit)?) = bitmapProvider.setListener(listener)

        @kotlin.OptIn(FlowPreview::class)
        fun startSleepTimer(delayMillis: Long) {
            timerJob?.cancel()

            timerJob = coroutineScope.timer(delayMillis) {
                ServiceNotifications.sleepTimer.sendNotification(this@PlayerService) {
                    this
                        .setContentTitle(getString(R.string.sleep_timer_ended))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .setShowWhen(true)
                        .setSmallIcon(R.drawable.app_icon)
                }

                handler.post {
                    player.pause()
                    player.stop()

                    glyphInterface.glyph {
                        turnOff()
                    }
                }
            }.also { job ->
                glyphInterface.progress(
                    job
                        .millisLeft
                        .takeWhile { it != null }
                        .debounce(500.milliseconds)
                        .map { ((it ?: 0L) / delayMillis.toFloat() * 100).toInt() }
                )
            }
        }

        fun cancelSleepTimer() {
            timerJob?.cancel()
            timerJob = null
        }

        fun setupRadio(endpoint: NavigationEndpoint.Endpoint.Watch?) =
            startRadio(endpoint = endpoint, justAdd = true)

        fun playRadio(endpoint: NavigationEndpoint.Endpoint.Watch?) =
            startRadio(endpoint = endpoint, justAdd = false)

        private fun startRadio(endpoint: NavigationEndpoint.Endpoint.Watch?, justAdd: Boolean) {
            radioJob?.cancel()
            radio = null

            YouTubeRadio(
                endpoint?.videoId,
                endpoint?.playlistId,
                endpoint?.playlistSetVideoId,
                endpoint?.params,
                dataSource = applicationContext.appContainer.youTubeRadioDataSource
            ).let { radioData ->
                isLoadingRadio = true
                radioJob = coroutineScope.launch {
                    val items = radioData.process().let { playerRepository.filterBlacklistedSongs(it) }
                    prefetchMediaItems(items)

                    withContext(Dispatchers.Main) {
                        if (justAdd) player.addMediaItems(items.drop(1))
                        else player.forcePlayFromBeginning(items)
                    }

                    radio = radioData
                    isLoadingRadio = false
                }
            }
        }

        fun stopRadio() {
            isLoadingRadio = false
            radioJob?.cancel()
            radio = null
        }

        fun prefetchMediaItems(mediaItems: List<MediaItem>) {
            this@PlayerService.prefetchMediaItems(mediaItems)
        }

        fun enqueue(mediaItem: MediaItem) {
            prefetchMediaItems(listOf(mediaItem))
            player.enqueue(mediaItem)
        }

        fun enqueue(mediaItems: List<MediaItem>) {
            prefetchMediaItems(mediaItems)
            player.enqueue(mediaItems)
        }

        /**
         * This method should ONLY be called when the application (sc. activity) is in the foreground!
         */
        fun restartForegroundOrStop() {
            player.pause()
            isInvincibilityEnabled = false
            stopSelf()
        }

        fun isCached(song: SongWithContentLength) =
            song.contentLength?.let { cache.isCached(song.song.id, 0L, it) } ?: false

        fun playFromSearch(query: String) {
            coroutineScope.launch {
                searchResultRepository
                    .searchSongs(query = query, continuation = null)
                    ?.getOrNull()
                    ?.items
                    ?.firstOrNull()
                    ?.info
                    ?.endpoint
                    ?.let { playRadio(it) }
            }
        }
    }

    private fun likeAction() = mediaItemState.value?.let { mediaItem ->
        runCatching {
            playerRepository.setLikedAt(
                songId = mediaItem.mediaId,
                likedAt = if (isLikedState.value) null else System.currentTimeMillis()
            )
        }
    }.let { }

    private fun shouldPrefetch(mediaItem: MediaItem) =
        !mediaItem.isLocal && mediaItem.mediaId.isNotBlank()

    private fun prefetchMediaItems(mediaItems: List<MediaItem>) {
        val ids = mediaItems
            .asSequence()
            .filter(::shouldPrefetch)
            .map { extractYouTubeVideoId(it.mediaId) }
            .distinct()
            .take(PREFETCH_MAX)
            .toList()

        if (ids.isEmpty()) return

        coroutineScope.launch(Dispatchers.IO) {
            ids.forEach { mediaId ->
                if (uriCache[mediaId] != null) return@forEach

                runCatching {
                    val result = NewPipeExtractorClient.resolveAudioStream(mediaId)
                    val streamInfo = result.streamInfo
                    if (streamInfo.id != mediaId) return@runCatching
                    val audioStream = result.audioStream
                    if (!audioStream.isUrl) return@runCatching

                    uriCache.push(
                        key = mediaId,
                        meta = null,
                        uri = audioStream.content.toUri(),
                        validUntil = null
                    )
                }.onFailure { it.printStackTrace() }
            }
        }
    }

    inner class NotificationActionReceiver internal constructor() :
        ActionReceiver("app.vimusic.android") {
        val pause by action { _, _ ->
            player.pause()
        }
        val play by action { _, _ ->
            player.play()
        }
        val next by action { _, _ ->
            player.forceSeekToNext()
        }
        val previous by action { _, _ ->
            player.forceSeekToPrevious()
        }
        val like by action { _, _ ->
            likeAction()
        }
    }

    class NotificationDismissReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            context.stopService(context.intent<PlayerService>())
        }
    }

    companion object {
        private const val DEFAULT_CACHE_DIRECTORY = "exoplayer"
        private const val DEFAULT_CHUNK_LENGTH = 2 * 1024 * 1024L
        private const val PREFETCH_MAX = 6
        private const val PROACTIVE_REFRESH_THRESHOLD_MS = 12_000L
        private const val PROACTIVE_REFRESH_POLL_INTERVAL_MS = 2_000L
        private const val PROACTIVE_REFRESH_MIN_INTERVAL_MS = 6_000L
        private const val PREVIEW_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
        private val preflightHttpClient by lazy {
            OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
        private val youtubeIdRegex = Regex("^[A-Za-z0-9_-]{11}$")

        private fun extractYouTubeVideoId(raw: String): String {
            val trimmed = raw.trim()
            if (youtubeIdRegex.matches(trimmed)) return trimmed

            val uri = runCatching { trimmed.toUri() }.getOrNull()
            if (uri != null) {
                if (uri.isHierarchical) {
                    uri.getQueryParameter("v")?.takeIf { it.isNotBlank() }?.let { return it }
                }

                val host = uri.host.orEmpty()
                val path = uri.path.orEmpty()
                if (host.endsWith("youtu.be")) {
                    uri.lastPathSegment?.takeIf { youtubeIdRegex.matches(it) }?.let { return it }
                }
                if (path.startsWith("/shorts/")) {
                    path.substringAfter("/shorts/")
                        .substringBefore("/")
                        .takeIf { youtubeIdRegex.matches(it) }
                        ?.let { return it }
                }
                if (path.startsWith("/embed/")) {
                    path.substringAfter("/embed/")
                        .substringBefore("/")
                        .takeIf { youtubeIdRegex.matches(it) }
                        ?.let { return it }
                }
            }

            Regex("[?&]v=([A-Za-z0-9_-]{11})")
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { return it }

            return trimmed
        }

        fun createDatabaseProvider(context: Context) = StandaloneDatabaseProvider(context)
        fun createCache(
            context: Context,
            directoryName: String = DEFAULT_CACHE_DIRECTORY,
            size: ExoPlayerDiskCacheSize = DataPreferences.exoPlayerDiskCacheMaxSize
        ) = with(context) {
            val cacheEvictor = when (size) {
                ExoPlayerDiskCacheSize.Unlimited -> NoOpCacheEvictor()
                else -> LeastRecentlyUsedCacheEvictor(size.bytes)
            }

            val directory = cacheDir.resolve(directoryName).apply {
                if (!exists()) mkdir()
            }

            SimpleCache(directory, cacheEvictor, createDatabaseProvider(context))
        }

        @Suppress("CyclomaticComplexMethod")
        fun createYouTubeDataSourceResolverFactory(
            context: Context,
            cache: Cache,
            chunkLength: Long? = DEFAULT_CHUNK_LENGTH,
            findMediaItem: suspend (videoId: String) -> MediaItem? = { null },
            uriCache: UriCache<String, Long?> = UriCache(),
            dbWriteScope: CoroutineScope? = null,
            playbackRetryManager: PlaybackRetryManager? = null
        ): DataSource.Factory {
            val playerRepository = context.appContainer.playerRepository

            return ResolvingDataSource.Factory(
            ConditionalCacheDataSourceFactory(
                cacheDataSourceFactory = cache.readOnlyWhen { PlayerPreferences.pauseCache }.asDataSource,
                upstreamDataSourceFactory = context.defaultDataSource,
                shouldCache = { dataSpec ->
                    if (dataSpec.isLocal) return@ConditionalCacheDataSourceFactory false
                    if (!DataPreferences.cacheFavoritesOnly) return@ConditionalCacheDataSourceFactory true

                    val mediaId = dataSpec.key?.let(::extractYouTubeVideoId) ?: return@ConditionalCacheDataSourceFactory false
                    playerRepository.isFavoriteNow(mediaId)
                }
            )
        ) resolver@{ dataSpec ->
            if (dataSpec.isLocal) return@resolver dataSpec

            val mediaId = dataSpec.key
                ?.let(::extractYouTubeVideoId)
                ?: run {
                    Log.w(TAG, "DataSpec key missing; skipping cache resolution")
                    return@resolver dataSpec
                }

            val forceFreshResolve = playbackRetryManager?.consumeForceFreshResolve(mediaId) == true

            fun DataSpec.ranged(contentLength: Long?) = contentLength?.let {
                if (chunkLength == null) return@let null

                val start = dataSpec.uriPositionOffset
                val length = (contentLength - start).coerceAtMost(chunkLength)
                val rangeText = "$start-${start + length}"

                this.subrange(start, length)
                    .withAdditionalHeaders(mapOf("Range" to "bytes=$rangeText"))
            } ?: this

            val resolvedDataSpec: DataSpec = run {
                val cachedDataSpec = if (!forceFreshResolve) {
                    uriCache[mediaId]?.let { cachedUri ->
                        dataSpec
                            .withUri(cachedUri.uri)
                            .ranged(cachedUri.meta)
                    }
                } else {
                    null
                }

                cachedDataSpec ?: run {
                    val result = try {
                        runBlocking(Dispatchers.IO) {
                            NewPipeExtractorClient.resolveAudioStream(mediaId)
                        }
                    } catch (error: ContentNotSupportedException) {
                        throw PlayableFormatNotFoundException(error)
                    } catch (error: StreamInfo.StreamExtractException) {
                        throw PlayableFormatNotFoundException(error)
                    } catch (error: ContentNotAvailableException) {
                        throw UnplayableException(error)
                    } catch (error: ReCaptchaException) {
                        throw LoginRequiredException(error)
                    } catch (error: ParsingException) {
                        throw UnplayableException(error)
                    } catch (error: ExtractionException) {
                        throw UnplayableException(error)
                    }

                    val streamInfo = result.streamInfo
                    if (streamInfo.id != mediaId) {
                        throw VideoIdMismatchException()
                    }

                    val audioStream = result.audioStream
                    if (!audioStream.isUrl) {
                        throw PlayableFormatNotFoundException()
                    }

                    val mediaItem = runCatching {
                        runBlocking(Dispatchers.IO) { findMediaItem(mediaId) }
                    }.getOrNull()
                    val extras = mediaItem?.mediaMetadata?.extras?.songBundle

                    if (extras?.durationText == null) {
                        val durationSeconds = streamInfo.duration
                        if (durationSeconds > 0) {
                            DateUtils.formatElapsedTime(durationSeconds)
                                .removePrefix("0")
                                .let { durationText ->
                                    extras?.durationText = durationText
                                    playerRepository.updateDurationText(mediaId, durationText)
                                }
                        }
                    }

                    val writeTask = {
                        runCatching {
                            mediaItem?.let { item ->
                                playerRepository.insertSong(item)
                            }
                            playerRepository.insertFormat(
                                Format(
                                    songId = mediaId,
                                    itag = audioStream.itag.takeIf { it > 0 },
                                    mimeType = audioStream.format?.mimeType,
                                    bitrate = audioStream.averageBitrate
                                        .takeIf { it > 0 }
                                        ?.toLong(),
                                    loudnessDb = null,
                                    contentLength = null,
                                    lastModified = null
                                )
                            )
                        }
                    }

                    if (dbWriteScope != null) {
                        dbWriteScope.launch(Dispatchers.IO) { writeTask() }
                    } else {
                        writeTask()
                    }

                    val uri = audioStream.content.toUri()

                    uriCache.push(
                        key = mediaId,
                        meta = null,
                        uri = uri,
                        validUntil = null
                    )

                    dataSpec
                        .withUri(uri)
                        .ranged(null)
                }
            }

            if (forceFreshResolve) resolvedDataSpec.withFreshConnectionHeaders() else resolvedDataSpec
        }
            .handleUnknownErrors {
                uriCache.clear()
            }
            .handleRangeErrors()
        }
    }
}

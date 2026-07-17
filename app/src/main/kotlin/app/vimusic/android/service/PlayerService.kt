package app.vimusic.android.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.util.Log
import androidx.annotation.DrawableRes
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
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionError
import java.io.ByteArrayOutputStream
import app.vimusic.android.MainActivity
import app.vimusic.android.R
import app.vimusic.android.appContainer
import app.vimusic.android.models.Event
import app.vimusic.android.models.Album
import app.vimusic.android.models.PlaylistPreview
import app.vimusic.android.models.QueuedMediaItem
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.AppearancePreferences
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.preferences.OrderPreferences
import app.vimusic.android.preferences.PlayerPreferences
import app.vimusic.android.utils.ActionReceiver
import app.vimusic.android.utils.GlyphInterface
import app.vimusic.android.utils.InvincibleService
import app.vimusic.android.utils.TimerJob
import app.vimusic.android.utils.YouTubeRadio
import app.vimusic.android.utils.YouTubeRadioState
import app.vimusic.android.utils.activityPendingIntent
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.broadcastPendingIntent
import app.vimusic.android.utils.enqueue
import app.vimusic.android.utils.findCause
import app.vimusic.android.utils.findNextMediaItemById
import app.vimusic.android.utils.forcePlayFromBeginning
import app.vimusic.android.utils.forcePlayAtIndex
import app.vimusic.android.utils.forceSeekToNext
import app.vimusic.android.utils.forceSeekToPrevious
import app.vimusic.android.utils.get
import app.vimusic.android.utils.InvalidPlaybackResponseException
import app.vimusic.android.utils.intent
import app.vimusic.android.utils.mediaItems
import app.vimusic.android.utils.PlaybackRetryManager
import app.vimusic.android.utils.progress
import app.vimusic.android.utils.safeUnregisterReceiver
import app.vimusic.android.utils.setPlaybackPitch
import app.vimusic.android.utils.shouldBePlaying
import app.vimusic.android.utils.thumbnail
import app.vimusic.android.utils.timer
import app.vimusic.android.utils.toast
import app.vimusic.compose.preferences.SharedPreferencesProperty
import app.vimusic.core.data.enums.ExoPlayerDiskCacheSize
import app.vimusic.core.ui.utils.EqualizerIntentBundleAccessor
import app.vimusic.core.ui.utils.isAtLeastAndroid10
import app.vimusic.core.ui.utils.isAtLeastAndroid6
import app.vimusic.core.ui.utils.isAtLeastAndroid8
import app.vimusic.core.ui.utils.isAtLeastAndroid9
import app.vimusic.core.ui.utils.songBundle
import app.vimusic.core.ui.utils.streamVolumeFlow
import app.vimusic.providers.youtubemusic.innertube.models.NavigationEndpoint
import app.vimusic.providers.sponsorblock.models.Action
import app.vimusic.providers.sponsorblock.models.Category
import app.vimusic.providers.sponsorblock.models.Segment
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import android.os.Binder as AndroidBinder
import android.os.IBinder

const val LOCAL_KEY_PREFIX = "local:"
private const val TAG = "PlayerService"
private const val PERSISTENT_QUEUE_MAX_PAST_ITEMS = 50
private const val PERSISTENT_QUEUE_MAX_FUTURE_ITEMS = 50
private const val PERSISTENT_QUEUE_SAVE_DEBOUNCE_MS = 500L
private const val SPONSOR_BLOCK_SEEK_POLL_DELAY_MS = 1_000L
private const val NEXT_TRACK_RESOLVE_PRELOAD_ATTEMPTS = 4
private const val NEXT_TRACK_RESOLVE_PRELOAD_RETRY_MS = 500L

@get:OptIn(UnstableApi::class)
val DataSpec.isLocal get() = key?.startsWith(LOCAL_KEY_PREFIX) == true

val MediaItem.isLocal get() = mediaId.startsWith(LOCAL_KEY_PREFIX)
val Song.isLocal get() = id.startsWith(LOCAL_KEY_PREFIX)

@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass", "TooManyFunctions") // intended in this class: it is a service
@OptIn(UnstableApi::class)
class PlayerService : InvincibleService(), Player.Listener, PlaybackStatsListener.Callback {
    private val playerRepository by lazy { applicationContext.appContainer.playerRepository }
    private val mediaLibraryRepository by lazy { applicationContext.appContainer.mediaLibraryRepository }
    private val searchResultRepository by lazy { applicationContext.appContainer.searchResultRepository }
    private val sponsorBlockRepository by lazy { applicationContext.appContainer.sponsorBlockRepository }
    private lateinit var mediaSession: MediaLibraryService.MediaLibrarySession
    private lateinit var cache: SimpleCache
    private lateinit var player: ExoPlayer

    private var timerJob: TimerJob? by mutableStateOf(null)
    private var radio: YouTubeRadio? = null
    private val playbackRetryManager = PlaybackRetryManager(longArrayOf(500L, 1500L, 3000L))
    private var lastAutoSongs = emptyList<Song>()
    // Android Auto asks for search results after onSearch; keep the YouTube results for selection.
    private var lastAutoSearchQuery: String? = null
    private var lastAutoSearchItems = emptyList<MediaItem>()

    private lateinit var bitmapProvider: BitmapProvider

    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var preferenceUpdaterJob: Job? = null
    private var volumeNormalizationJob: Job? = null
    private var sponsorBlockJob: Job? = null
    private var queuePersistenceJob: Job? = null
    private var nextTrackPreloadJob: Job? = null
    private var nextTrackPreloadMediaId: String? = null

    override var isInvincibilityEnabled by mutableStateOf(false)

    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null
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

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent) ?: binder
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibraryService.MediaLibrarySession = mediaSession

    private fun browseItem(
        id: String,
        title: String,
        subtitle: String? = null,
        iconUri: Uri? = null,
        isBrowsable: Boolean,
        isPlayable: Boolean
    ) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(iconUri)
                .setIsBrowsable(isBrowsable)
                .setIsPlayable(isPlayable)
                .build()
        )
        .build()

    private fun uriFor(@DrawableRes id: Int) = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(resources.getResourcePackageName(id))
        .appendPath(resources.getResourceTypeName(id))
        .appendPath(resources.getResourceEntryName(id))
        .build()

    private val rootMediaItem
        get() = browseItem(
            id = AutoMediaId.ROOT.id,
            title = applicationInfo.loadLabel(packageManager).toString(),
            isBrowsable = true,
            isPlayable = false
        )

    private val songsMediaItem
        get() = browseItem(
            id = AutoMediaId.SONGS.id,
            title = getString(R.string.songs),
            iconUri = uriFor(R.drawable.musical_notes),
            isBrowsable = true,
            isPlayable = false
        )

    private val playlistsMediaItem
        get() = browseItem(
            id = AutoMediaId.PLAYLISTS.id,
            title = getString(R.string.playlists),
            iconUri = uriFor(R.drawable.playlist),
            isBrowsable = true,
            isPlayable = false
        )

    private val albumsMediaItem
        get() = browseItem(
            id = AutoMediaId.ALBUMS.id,
            title = getString(R.string.albums),
            iconUri = uriFor(R.drawable.disc),
            isBrowsable = true,
            isPlayable = false
        )

    private val favoritesMediaItem
        get() = browseItem(
            id = AutoMediaId.FAVORITES.id,
            title = getString(R.string.favorites),
            iconUri = uriFor(R.drawable.heart),
            isBrowsable = false,
            isPlayable = true
        )

    private val offlineMediaItem
        get() = browseItem(
            id = AutoMediaId.OFFLINE.id,
            title = getString(R.string.offline),
            iconUri = uriFor(R.drawable.airplane),
            isBrowsable = false,
            isPlayable = true
        )

    private val topMediaItem
        get() = browseItem(
            id = AutoMediaId.TOP.id,
            title = getString(
                R.string.format_my_top_playlist,
                DataPreferences.topListLength.toString()
            ),
            iconUri = uriFor(R.drawable.trending),
            isBrowsable = false,
            isPlayable = true
        )

    private val localMediaItem
        get() = browseItem(
            id = AutoMediaId.LOCAL.id,
            title = getString(R.string.local),
            iconUri = uriFor(R.drawable.download),
            isBrowsable = false,
            isPlayable = true
        )

    private val shuffleMediaItem
        get() = browseItem(
            id = AutoMediaId.SHUFFLE.id,
            title = getString(R.string.shuffle),
            iconUri = uriFor(R.drawable.shuffle),
            isBrowsable = false,
            isPlayable = true
        )

    private val Song.asAutoBrowsableMediaItem
        get() = browseItem(
            id = (AutoMediaId.SONGS / id).id,
            title = title,
            subtitle = artistsText,
            iconUri = thumbnailUrl?.toUri(),
            isBrowsable = false,
            isPlayable = true
        )

    private val PlaylistPreview.asAutoBrowsableMediaItem
        get() = browseItem(
            id = (AutoMediaId.PLAYLISTS / playlist.id.toString()).id,
            title = playlist.name,
            subtitle = resources.getQuantityString(
                R.plurals.song_count_plural,
                songCount,
                songCount
            ),
            iconUri = uriFor(R.drawable.playlist),
            isBrowsable = false,
            isPlayable = true
        )

    private val Album.asAutoBrowsableMediaItem
        get() = browseItem(
            id = (AutoMediaId.ALBUMS / id).id,
            title = title.orEmpty(),
            subtitle = authorsText.orEmpty(),
            iconUri = thumbnailUrl?.toUri(),
            isBrowsable = false,
            isPlayable = true
        )

    // Android Auto needs explicit browsable/playable metadata to show YouTube search results.
    private val MediaItem.asAutoSearchResult
        get() = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaId)
            .setCustomCacheKey(mediaId)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(mediaMetadata.title)
                    .setSubtitle(mediaMetadata.artist)
                    .setArtist(mediaMetadata.artist)
                    .setAlbumTitle(mediaMetadata.albumTitle)
                    .setArtworkUri(mediaMetadata.artworkUri)
                    .setExtras(mediaMetadata.extras)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

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
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ PLAYBACK_MIN_BUFFER_MS,
                        /* maxBufferMs = */ PLAYBACK_MAX_BUFFER_MS,
                        /* bufferForPlaybackMs = */ PLAYBACK_START_BUFFER_MS,
                        /* bufferForPlaybackAfterRebufferMs = */ PLAYBACK_REBUFFER_MS,
                    )
                    // Keep filling the current item even after the allocator's
                    // byte target is reached. This gives HTTP/3 range retries
                    // enough buffered audio to complete without an audible gap.
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
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

        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, player, AutoSessionCallback())
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

        preferenceUpdaterJob = coroutineScope.launch {
            fun <T : Any> subscribe(
                prop: SharedPreferencesProperty<T>,
                callback: (T) -> Unit
            ) = launch { prop.stateFlow.collectLatest { handler.post { callback(it) } } }

            subscribe(AppearancePreferences.isShowingThumbnailInLockscreenProperty) {
                maybeShowSongCoverInLockScreen()
            }

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
                    handler.post {
                        if (it == min && PlayerPreferences.pauseWhenVolumeAtMinimum) player.pause()
                    }
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
            maybeSavePlayerQueue(immediately = true)

            player.removeListener(this)
            player.stop()
            player.release()

            safeUnregisterReceiver(notificationActionReceiver)

            mediaSession.release()
            cache.release()

            loudnessEnhancer?.release()
            preferenceUpdaterJob?.cancel()

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
        prefetchNextMediaItem()

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
        // Queue insertion can reveal a next item without causing a media-item
        // transition. Start URL extraction as soon as it becomes available.
        prefetchNextMediaItem()
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        player.currentMediaItem?.mediaId?.let { mediaId ->
            if (error.findCause<InvalidPlaybackResponseException>() != null) {
                playbackRetryManager.prepareRetry(mediaId) {}
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

                        playbackRetryManager.prepareRetry(mediaId) {}

                        player.prepare()
                        player.play()
                    },
                    retryDelayMs
                )
                return
            }
        }

        return
    }

    private fun maybeRecoverPlaybackError() {
        if (player.playerError != null) player.prepare()
    }

    private fun maybeProcessRadio() {
        if (player.mediaItemCount - player.currentMediaItemIndex > 3) return

        radio?.let { radio ->
            coroutineScope.launch(Dispatchers.Main) {
                val radioItems = radio.process().map { it.withRadioState(radio) }
                syncCurrentMediaItemRadioState(radio)
                player.addMediaItems(radioItems)
            }
        }
    }

    private fun maybeSavePlayerQueue(immediately: Boolean = false) {
        if (!PlayerPreferences.persistentQueue) return

        queuePersistenceJob?.cancel()
        queuePersistenceJob = coroutineScope.launch {
            if (!immediately) delay(PERSISTENT_QUEUE_SAVE_DEBOUNCE_MS)

            val queue = withContext(Dispatchers.Main) {
                val mediaItems = player.currentTimeline.mediaItems
                val mediaItemIndex = player.currentMediaItemIndex
                val mediaItemPosition = player.currentPosition
                if (mediaItems.isEmpty() || mediaItemIndex !in mediaItems.indices) return@withContext null

                val startIndex = (mediaItemIndex - PERSISTENT_QUEUE_MAX_PAST_ITEMS).coerceAtLeast(0)
                val endExclusive = (mediaItemIndex + PERSISTENT_QUEUE_MAX_FUTURE_ITEMS + 1)
                    .coerceAtMost(mediaItems.size)
                val persistedCurrentIndex = mediaItemIndex - startIndex

                mediaItems.subList(startIndex, endExclusive).mapIndexed { index, mediaItem ->
                    QueuedMediaItem(
                        id = index.toLong() + 1L,
                        mediaItem = mediaItem,
                        position = if (index == persistedCurrentIndex) mediaItemPosition else null
                    )
                }
            } ?: return@launch

            runCatching {
                playerRepository.saveQueue(queue)
            }
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
            val restoredRadio = queue.getOrNull(index)
                ?.mediaItem
                ?.radioStateOrNull()
                ?.let(::radioFromState)

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
                    radio = restoredRadio

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
            if (lastSegmentEnd <= posMillis()) {
                // Keep the loop available for a user seeking backwards, without spinning after
                // the final segment has been passed.
                delay(SPONSOR_BLOCK_SEEK_POLL_DELAY_MS)
                continue
            }

            val nextSegment = segments.firstOrNull { posMillis() < it.end.inWholeMilliseconds }
                ?: return

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
            updateSessionArtworkData(mediaMetadata.artworkUri, it)
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

    /**
     * SystemUI does not fetch arbitrary HTTPS artwork URIs from another
     * process. Keep the URI for in-app Coil rendering, but publish the fetched
     * and downsized bitmap as Media3 artworkData for notification/lockscreen.
     */
    private fun updateSessionArtworkData(artworkUri: Uri?, bitmap: Bitmap) {
        val currentItem = player.currentMediaItem ?: return
        if (currentItem.mediaMetadata.artworkUri != artworkUri || bitmapProvider.lastUri != artworkUri) return
        val largestSide = maxOf(bitmap.width, bitmap.height)
        val encodedBitmap = if (largestSide > SYSTEM_ARTWORK_MAX_PIXELS) {
            val scale = SYSTEM_ARTWORK_MAX_PIXELS.toFloat() / largestSide
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else bitmap
        val artworkData = ByteArrayOutputStream().use { output ->
            encodedBitmap.compress(Bitmap.CompressFormat.JPEG, ARTWORK_JPEG_QUALITY, output)
            output.toByteArray()
        }
        if (encodedBitmap !== bitmap) encodedBitmap.recycle()
        if (currentItem.mediaMetadata.artworkData?.contentEquals(artworkData) == true) return
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            currentItem.buildUpon()
                .setMediaMetadata(currentItem.mediaMetadata.buildUpon().setArtworkData(artworkData).build())
                .build(),
        )
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

    private fun createMediaSourceFactory() = NewPipeAudioMediaSourceFactory(
        context = applicationContext,
        cache = cache,
            findMediaItem = { videoId ->
                withContext(Dispatchers.Main) {
                    player.findNextMediaItemById(videoId)
                }
            },
        dbWriteScope = coroutineScope
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
    private inner class AutoSessionCallback : MediaLibraryService.MediaLibrarySession.Callback {
        @Suppress("CyclomaticComplexMethod")
        override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            coroutineScope.launch {
                val result = runCatching {
                    val items = when (AutoMediaId(parentId)) {
                        AutoMediaId.ROOT -> mutableListOf(
                            songsMediaItem,
                            playlistsMediaItem,
                            albumsMediaItem
                        )

                        AutoMediaId.SONGS ->
                            mediaLibraryRepository
                                .getRecentSongs(limit = 30)
                                .also { lastAutoSongs = it }
                                .map { it.asAutoBrowsableMediaItem }
                                .toMutableList()
                                .apply {
                                    if (isNotEmpty()) add(0, shuffleMediaItem)
                                }

                        AutoMediaId.PLAYLISTS ->
                            mediaLibraryRepository
                                .getPlaylistPreviewsByDateAddedDesc()
                                .map { it.asAutoBrowsableMediaItem }
                                .toMutableList()
                                .apply {
                                    add(0, favoritesMediaItem)
                                    add(1, offlineMediaItem)
                                    add(2, topMediaItem)
                                    add(3, localMediaItem)
                                }

                        AutoMediaId.ALBUMS ->
                            mediaLibraryRepository
                                .getAlbumsByRowIdDesc()
                                .map { it.asAutoBrowsableMediaItem }
                                .toMutableList()

                        else -> mutableListOf()
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                }

                future.set(
                    result.getOrElse {
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                )
            }

            return future
        }

        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            LibraryResult.ofItem(
                rootMediaItem,
                MediaLibraryService.LibraryParams.Builder()
                    .setExtras(Bundle().apply {
                        putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                        putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                        putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
                    })
                    .build()
            )
        )

        override fun onGetItem(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (AutoMediaId(mediaId)) {
                AutoMediaId.ROOT -> rootMediaItem
                AutoMediaId.SONGS -> songsMediaItem
                AutoMediaId.PLAYLISTS -> playlistsMediaItem
                AutoMediaId.ALBUMS -> albumsMediaItem
                AutoMediaId.FAVORITES -> favoritesMediaItem
                AutoMediaId.OFFLINE -> offlineMediaItem
                AutoMediaId.TOP -> topMediaItem
                AutoMediaId.LOCAL -> localMediaItem
                AutoMediaId.SHUFFLE -> shuffleMediaItem
                else -> null
            }

            return Futures.immediateFuture(
                if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                else LibraryResult.ofItem(item, null)
            )
        }

        override fun onSearch(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isNotBlank()) {
                coroutineScope.launch {
                    // Use the same YouTube Music search source as the in-app search screen.
                    val items = runCatching {
                        searchResultRepository
                            .searchSongs(query = trimmedQuery, continuation = null)
                            ?.getOrThrow()
                            ?.items
                            .orEmpty()
                            .map { it.asMediaItem.asAutoSearchResult }
                    }.getOrDefault(emptyList())

                    lastAutoSearchQuery = trimmedQuery
                    lastAutoSearchItems = items

                    withContext(Dispatchers.Main) {
                        session.notifySearchResultChanged(
                            controller,
                            trimmedQuery,
                            items.size,
                            params
                        )
                    }
                }
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibraryService.MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            coroutineScope.launch {
                val result = runCatching {
                    // Return YouTube search results to the Android Auto search template.
                    val safePage = page.coerceAtLeast(0)
                    val safePageSize = pageSize.coerceIn(1, 50)
                    val trimmedQuery = query.trim()
                    val items = query
                        .trim()
                        .takeIf(String::isNotBlank)
                        ?.let { trimmedQuery ->
                            val cached = lastAutoSearchItems.takeIf {
                                lastAutoSearchQuery == trimmedQuery && it.isNotEmpty()
                            }
                            cached ?: runCatching {
                                searchResultRepository
                                    .searchSongs(query = trimmedQuery, continuation = null)
                                    ?.getOrThrow()
                                    ?.items
                                    .orEmpty()
                                    .map { it.asMediaItem.asAutoSearchResult }
                            }.getOrDefault(emptyList())
                                .also {
                                    lastAutoSearchQuery = trimmedQuery
                                    lastAutoSearchItems = it
                                }
                        }
                        .orEmpty()

                    val from = safePage * safePageSize
                    val pageItems = if (from >= items.size) emptyList()
                    else items.subList(from, minOf(from + safePageSize, items.size))

                    LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params)
                }

                future.set(
                    result.getOrElse {
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                )
            }

            return future
        }

        @Suppress("CyclomaticComplexMethod")
        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            coroutineScope.launch {
                val resolved = runCatching {
                    val item = mediaItems.firstOrNull() ?: return@runCatching mediaItems
                    val data = item.mediaId.split('/')
                    var index = 0

                    val cachedSearchItem = lastAutoSearchItems.firstOrNull { it.mediaId == item.mediaId }
                    if (cachedSearchItem != null) return@runCatching listOf(cachedSearchItem)

                    val mediaList = when (data.getOrNull(0)?.let { AutoMediaId(it) }) {
                        AutoMediaId.SHUFFLE -> lastAutoSongs

                        AutoMediaId.SONGS -> data.getOrNull(1)?.let { songId ->
                            index = lastAutoSongs.indexOfFirst { it.id == songId }
                            lastAutoSongs
                        }

                        AutoMediaId.FAVORITES ->
                            mediaLibraryRepository.getFavoritesShuffled()

                        AutoMediaId.OFFLINE ->
                            mediaLibraryRepository.getOfflineCachedShuffled { binder.isCached(it) }

                        AutoMediaId.TOP -> {
                            val duration = DataPreferences.topListPeriod.duration
                            val length = DataPreferences.topListLength

                            mediaLibraryRepository.getTopSongs(
                                durationMillis = duration?.inWholeMilliseconds,
                                length = length
                            )
                        }

                        AutoMediaId.LOCAL ->
                            mediaLibraryRepository.getLocalSongs(
                                sortBy = OrderPreferences.localSongSortBy,
                                sortOrder = OrderPreferences.localSongSortOrder
                            )

                        AutoMediaId.PLAYLISTS ->
                            data
                                .getOrNull(1)
                                ?.toLongOrNull()
                                ?.let { playlistId ->
                                    mediaLibraryRepository.getPlaylistSongsShuffled(playlistId)
                                }

                        AutoMediaId.ALBUMS ->
                            data
                                .getOrNull(1)
                                ?.let { albumId ->
                                    mediaLibraryRepository.getAlbumSongs(albumId)
                                }

                        else -> emptyList()
                    }

                    (mediaList?.map(Song::asMediaItem) ?: emptyList()).also {
                        if (it.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                player.forcePlayAtIndex(
                                    items = it,
                                    index = index.coerceIn(0, it.lastIndex.coerceAtLeast(0))
                                )
                            }
                        }
                    }
                }
                future.set(resolved.getOrElse { mediaItems })
            }

            return future
        }
    }

    @JvmInline
    private value class AutoMediaId(val id: String) : CharSequence by id {
        companion object {
            val ROOT = AutoMediaId("root")
            val SONGS = AutoMediaId("songs")
            val PLAYLISTS = AutoMediaId("playlists")
            val ALBUMS = AutoMediaId("albums")

            val FAVORITES = AutoMediaId("favorites")
            val OFFLINE = AutoMediaId("offline")
            val TOP = AutoMediaId("top")
            val LOCAL = AutoMediaId("local")
            val SHUFFLE = AutoMediaId("shuffle")
        }

        operator fun div(other: String) = AutoMediaId("$id/$other")
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
                    val items = radioData.process()
                        .map { it.withRadioState(radioData) }
                        .let { playerRepository.filterBlacklistedSongs(it) }
                    prefetchMediaItems(items)

                    withContext(Dispatchers.Main) {
                        syncCurrentMediaItemRadioState(radioData)
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

        fun isCached(mediaId: String) = isFullyCached(cache, mediaId)

        fun isCached(song: Song) = isCached(song.id)

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

    private fun prefetchMediaItems(mediaItems: List<MediaItem>) {
        mediaItems.firstOrNull()?.let(::prefetchMediaItem)
    }

    private fun prefetchNextMediaItem() {
        player.currentMediaItemIndex
            .plus(1)
            .takeIf { it in 0 until player.mediaItemCount }
            ?.let(player::getMediaItemAt)
            ?.let(::prefetchMediaItem)
    }

    private fun prefetchMediaItem(mediaItem: MediaItem) {
        if (mediaItem.isLocal) return
        if (
            nextTrackPreloadMediaId == mediaItem.mediaId &&
            nextTrackPreloadJob?.isActive == true
        ) return

        nextTrackPreloadJob?.cancel()
        nextTrackPreloadMediaId = mediaItem.mediaId
        nextTrackPreloadJob = coroutineScope.launch {
            repeat(NEXT_TRACK_RESOLVE_PRELOAD_ATTEMPTS) { attempt ->
                val preloaded = runCatching {
                    NewPipeAudioMediaSourceFactory.preloadAudioResult(mediaItem.mediaId)
                }.onFailure { error ->
                    Log.d(TAG, "Failed to preload next track ${mediaItem.mediaId}", error)
                }.getOrDefault(false)

                if (preloaded) {
                    Log.d(TAG, "Preloaded next track ${mediaItem.mediaId} attempt=${attempt + 1}")
                    return@launch
                }
                if (attempt + 1 < NEXT_TRACK_RESOLVE_PRELOAD_ATTEMPTS) {
                    delay(NEXT_TRACK_RESOLVE_PRELOAD_RETRY_MS)
                }
            }
            Log.d(TAG, "Next track preload remained busy for ${mediaItem.mediaId}")
        }
    }

    private fun syncCurrentMediaItemRadioState(radio: YouTubeRadio) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex !in 0 until player.mediaItemCount) return

        player.replaceMediaItem(currentIndex, player.getMediaItemAt(currentIndex).withRadioState(radio))
    }

    private fun MediaItem.withRadioState(radio: YouTubeRadio): MediaItem {
        val radioState = radio.snapshot()
        val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply {
            songBundle.apply {
                isRadio = true
                radioVideoId = radioState.videoId ?: this@withRadioState.mediaId
                radioPlaylistId = radioState.playlistId
                radioPlaylistSetVideoId = radioState.playlistSetVideoId
                radioParams = radioState.params
                radioContinuation = radioState.continuation
            }
        }

        return buildUpon()
            .setMediaMetadata(
                mediaMetadata.buildUpon()
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun MediaItem.radioStateOrNull(): YouTubeRadioState? {
        val extras = mediaMetadata.extras?.songBundle ?: return null
        if (!extras.isRadio) return null

        return YouTubeRadioState(
            videoId = extras.radioVideoId ?: mediaId,
            playlistId = extras.radioPlaylistId,
            playlistSetVideoId = extras.radioPlaylistSetVideoId,
            params = extras.radioParams,
            continuation = extras.radioContinuation
        )
    }

    private fun radioFromState(state: YouTubeRadioState): YouTubeRadio = YouTubeRadio(
        videoId = state.videoId,
        playlistId = state.playlistId,
        playlistSetVideoId = state.playlistSetVideoId,
        parameters = state.params,
        nextContinuation = state.continuation,
        dataSource = applicationContext.appContainer.youTubeRadioDataSource
    )

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
        private const val PLAYBACK_MIN_BUFFER_MS = 120_000
        private const val PLAYBACK_MAX_BUFFER_MS = 300_000
        private const val PLAYBACK_START_BUFFER_MS = 2_500
        private const val PLAYBACK_REBUFFER_MS = 5_000
        private const val ARTWORK_JPEG_QUALITY = 85
        private const val SYSTEM_ARTWORK_MAX_PIXELS = 320
        private val youtubeIdRegex = Regex("^[A-Za-z0-9_-]{11}$")

        fun extractYouTubeVideoId(raw: String): String {
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

        private fun isFullyCached(cache: Cache, mediaId: String): Boolean {
            val contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(mediaId))
            if (contentLength <= 0L) return false
            return cache.isCached(mediaId, 0L, contentLength)
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

    }
}

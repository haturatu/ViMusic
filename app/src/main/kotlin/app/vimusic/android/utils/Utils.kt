@file:OptIn(UnstableApi::class)

package app.vimusic.android.utils

import android.content.ContentUris
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.AppearancePreferences
import app.vimusic.android.service.LOCAL_KEY_PREFIX
import app.vimusic.android.service.isLocal
import app.vimusic.core.ui.utils.SongBundleAccessor
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.youtubemusic.innertube.requests.playlistPage
import app.vimusic.providers.piped.models.Playlist
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration
import java.net.SocketTimeoutException

/**
 * Chooses a compatible YouTube thumbnail host by observed reliability on the
 * current network. Host substitution is intentionally limited to the known
 * interchangeable yt3 endpoints; every other URL is left untouched.
 */
object YoutubeThumbnailHostResolver {
    private val lock = Any()
    private val stats = mutableMapOf<String, HostStats>()
    private var activeNetwork: Network? = null
    private var started = false
    private var connectivity: ConnectivityManager? = null

    fun initialize(context: Context) {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        synchronized(lock) {
            if (started) return
            started = true
            connectivity = manager
            activeNetwork = manager.activeNetwork
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = resetForNetwork(network)
                override fun onLost(network: Network) = synchronized(lock) {
                    if (activeNetwork == network) resetForNetworkLocked(null)
                }
            })
        }
    }

    fun resolve(url: String): String {
        refreshLegacyNetwork()
        val uri = Uri.parse(url)
        val originalHost = uri.host?.lowercase() ?: return url
        if (originalHost !in supportedHosts) return url
        val now = SystemClock.elapsedRealtime()
        val selectedHost = synchronized(lock) {
            // Host rewriting is an availability fallback, not an exploration
            // mechanism. Preserve the API-provided host until it has a real
            // transport failure streak; hosts are not guaranteed object-wise
            // interchangeable.
            val original = stats[originalHost]
            if (original == null || original.blockedUntilElapsedMillis <= now) return@synchronized originalHost
            (listOf(originalHost) + supportedHosts)
                .distinct()
                .filter { it != originalHost }
                .maxByOrNull { host -> stats[host]?.stabilityScore(now) ?: defaultScore(host, originalHost) }
        } ?: originalHost
        return if (selectedHost == originalHost) url else uri.buildUpon().authority(selectedHost).build().toString()
    }

    fun recordResponse(url: String, status: Int, contentType: String?, elapsedMillis: Long) {
        val host = Uri.parse(url).host?.lowercase()?.takeIf { it in supportedHosts } ?: return
        synchronized(lock) {
            val state = stats.getOrPut(host, ::HostStats)
            when {
                status in 200..299 && contentType?.startsWith("image/", ignoreCase = true) == true ->
                    state.recordSuccess(elapsedMillis)
                status in 500..599 -> state.recordHttp5xx()
                // 4xx is URL/auth related, not evidence that this host is unstable.
            }
        }
    }

    fun recordFailure(url: String, error: Throwable) {
        val host = Uri.parse(url).host?.lowercase()?.takeIf { it in supportedHosts } ?: return
        synchronized(lock) {
            val state = stats.getOrPut(host, ::HostStats)
            if (error.findCause<SocketTimeoutException>() != null ||
                error.findCause<kotlinx.coroutines.TimeoutCancellationException>() != null
            ) state.recordTimeout() else state.recordTransportFailure()
        }
    }

    fun shouldRetryOriginal(originalUrl: String, resolvedUrl: String, status: Int, contentType: String?, bodySize: Int): Boolean =
        originalUrl != resolvedUrl &&
            (status in setOf(403, 404, 410, 421) ||
                contentType?.startsWith("image/", ignoreCase = true) != true || bodySize == 0)

    private fun refreshLegacyNetwork() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return
        val network = connectivity?.activeNetwork
        synchronized(lock) { if (network != activeNetwork) resetForNetworkLocked(network) }
    }

    private fun resetForNetwork(network: Network?) = synchronized(lock) { resetForNetworkLocked(network) }

    private fun resetForNetworkLocked(network: Network?) {
        if (network == activeNetwork) return
        activeNetwork = network
        stats.clear()
    }

    private fun defaultScore(host: String, originalHost: String): Double = when {
        host == originalHost -> PRIOR_SCORE + 300.0
        host == "yt3.googleusercontent.com" -> PRIOR_SCORE + 200.0
        host == "yt3.ggpht.com" -> PRIOR_SCORE + 100.0
        else -> PRIOR_SCORE
    }

    private data class HostStats(
        var successes: Int = 0,
        var transportFailures: Int = 0,
        var timeouts: Int = 0,
        var http5xx: Int = 0,
        var latencyEwmaMillis: Double = 500.0,
        var consecutiveFailures: Int = 0,
        var blockedUntilElapsedMillis: Long = 0,
    ) {
        fun stabilityScore(now: Long): Double {
            if (blockedUntilElapsedMillis > now) return Double.NEGATIVE_INFINITY
            val weightedSuccesses = successes + 8.0
            val weightedFailures = transportFailures * 2.0 + timeouts * 3.0 + http5xx * 0.5 + 2.0
            return weightedSuccesses / (weightedSuccesses + weightedFailures) * 10_000.0 -
                latencyEwmaMillis * 0.2 - consecutiveFailures * 1_000.0
        }

        fun recordSuccess(elapsedMillis: Long) {
            successes++
            consecutiveFailures = 0
            blockedUntilElapsedMillis = 0
            latencyEwmaMillis = latencyEwmaMillis * 0.8 + elapsedMillis * 0.2
        }

        fun recordTransportFailure() = recordFailure(2)
        fun recordTimeout() = recordFailure(3)
        fun recordHttp5xx() = recordFailure(1)

        private fun recordFailure(weight: Int) {
            transportFailures += if (weight == 2) 1 else 0
            timeouts += if (weight == 3) 1 else 0
            http5xx += if (weight == 1) 1 else 0
            consecutiveFailures++
            val cooldown = when (consecutiveFailures) {
                1 -> 0L
                2 -> 10_000L
                3 -> 30_000L
                4 -> 120_000L
                else -> 600_000L
            }
            blockedUntilElapsedMillis = SystemClock.elapsedRealtime() + cooldown
        }
    }

    private val supportedHosts = listOf(
        "lh3.googleusercontent.com",
        "yt3.googleusercontent.com",
        "yt3.ggpht.com",
        "yt4.ggpht.com",
    )
    private const val PRIOR_SCORE = 7_900.0
}

val YoutubeMusicInnertube.SongItem.asMediaItem: MediaItem
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info?.name)
                .setArtist(authors?.joinToString("") { it.name.orEmpty() })
                .setAlbumTitle(album?.name)
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    SongBundleAccessor.bundle {
                        albumId = album?.endpoint?.browseId
                        durationText = this@asMediaItem.durationText
                        artistNames = authors
                            ?.filter { it.endpoint != null }
                            ?.mapNotNull { it.name }
                        artistIds = authors?.mapNotNull { it.endpoint?.browseId }
                        explicit = this@asMediaItem.explicit
                    }
                )
                .build()
        )
        .build()

val YoutubeMusicInnertube.VideoItem.asMediaItem: MediaItem
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info?.name)
                .setArtist(authors?.joinToString("") { it.name.orEmpty() })
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    SongBundleAccessor.bundle {
                        durationText = this@asMediaItem.durationText
                        artistNames = if (isOfficialMusicVideo) authors
                            ?.filter { it.endpoint != null }
                            ?.mapNotNull { it.name }
                        else null
                        artistIds = if (isOfficialMusicVideo) authors
                            ?.mapNotNull { it.endpoint?.browseId }
                        else null
                    }
                )
                .build()
        )
        .build()

val Playlist.Video.asMediaItem: MediaItem?
    get() {
        val key = id ?: return null

        return MediaItem.Builder()
            .setMediaId(key)
            .setUri(key)
            .setCustomCacheKey(key)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(uploaderName)
                    .setArtworkUri(thumbnailUrl.toString().toUri())
                    .setExtras(
                        SongBundleAccessor.bundle {
                            durationText = duration.toComponents { minutes, seconds, _ ->
                                "$minutes:${seconds.toString().padStart(2, '0')}"
                            }
                            artistNames = listOf(uploaderName)
                            artistIds = uploaderId?.let { listOf(it) }
                        }
                    )
                    .build()
            )
            .build()
    }

val Song.asMediaItem: MediaItem
    get() = MediaItem.Builder()
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistsText)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setExtras(
                    SongBundleAccessor.bundle {
                        durationText = this@asMediaItem.durationText
                        explicit = this@asMediaItem.explicit
                    }
                )
                .build()
        )
        .setMediaId(id)
        .setUri(
            if (isLocal) ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id.substringAfter(LOCAL_KEY_PREFIX).toLong()
            ) else id.toUri()
        )
        .setCustomCacheKey(id)
        .build()

val Duration.formatted
    @Composable get() = toComponents { hours, minutes, _, _ ->
        when {
            hours == 0L -> stringResource(id = R.string.format_minutes, minutes)
            hours < 24L -> stringResource(id = R.string.format_hours, hours)
            else -> stringResource(id = R.string.format_days, hours / 24)
        }
    }

fun String?.thumbnail(
    size: Int,
    maxSize: Int = AppearancePreferences.maxThumbnailSize
): String? {
    val actualSize = size.coerceAtMost(maxSize)
    return when {
        this?.startsWith("https://lh3.googleusercontent.com") == true -> "$this-w$actualSize-h$actualSize"
        this?.startsWith("https://yt3.ggpht.com") == true -> "$this-w$actualSize-h$actualSize-s$actualSize"
        this?.startsWith("https://yt3.googleusercontent.com") == true ->
            replace(Regex("=w\\d+-h\\d+[^/?]*"), "=w$actualSize-h$actualSize")
        else -> this
    }
}

fun Uri?.thumbnail(size: Int) = toString().thumbnail(size)?.toUri()

fun formatAsDuration(millis: Long) = DateUtils.formatElapsedTime(millis / 1000).removePrefix("0")

@Suppress("LoopWithTooManyJumpStatements")
suspend fun Result<YoutubeMusicInnertube.PlaylistOrAlbumPage>.completed(
    maxDepth: Int = Int.MAX_VALUE,
    shouldDedup: Boolean = false
) = runSuspendCatching {
    val page = getOrThrow()
    val songs = page.songsPage?.items.orEmpty().toMutableList()

    if (songs.isEmpty()) return@runSuspendCatching page

    var continuation = page.songsPage?.continuation
    var depth = 0
    val knownSongs = if (shouldDedup) songs.toHashSet() else null

    while (continuation != null && depth++ < maxDepth) {
        currentCoroutineContext().ensureActive()
        val newSongs = loadPlaylistContinuation(continuation)

        val incomingSongs = newSongs.items.orEmpty()
        if (shouldDedup && knownSongs != null && incomingSongs.any { it in knownSongs }) break

        if (incomingSongs.isNotEmpty()) {
            songs += incomingSongs
            knownSongs?.addAll(incomingSongs)
        }
        continuation = newSongs.continuation
    }

    page.copy(
        songsPage = YoutubeMusicInnertube.ItemsPage(
            items = songs,
            continuation = null
        )
    )
}.also { it.exceptionOrNull()?.printStackTrace() }

private suspend fun loadPlaylistContinuation(
    continuation: String,
): YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.SongItem> {
    var lastFailure: Throwable = IllegalStateException("Playlist continuation returned no result")

    repeat(PLAYLIST_CONTINUATION_ATTEMPTS) { attempt ->
        currentCoroutineContext().ensureActive()

        val result = YoutubeMusicInnertube.playlistPage(
            body = ContinuationBody(continuation = continuation),
        )

        result?.fold(
            onSuccess = { page ->
                if (!page.items.isNullOrEmpty()) return page
                lastFailure = IllegalStateException("Playlist continuation returned no songs")
            },
            onFailure = { error -> lastFailure = error },
        ) ?: run {
            lastFailure = IllegalStateException("Playlist continuation request was cancelled")
        }

        if (attempt < PLAYLIST_CONTINUATION_ATTEMPTS - 1) {
            delay(PLAYLIST_CONTINUATION_RETRY_DELAYS_MILLIS[attempt])
        }
    }

    throw lastFailure
}

private const val PLAYLIST_CONTINUATION_ATTEMPTS = 3
private val PLAYLIST_CONTINUATION_RETRY_DELAYS_MILLIS = longArrayOf(500L, 1_500L)

fun <T> Flow<T>.onFirst(block: suspend (T) -> Unit): Flow<T> {
    var isFirst = true

    return onEach {
        if (!isFirst) return@onEach

        block(it)
        isFirst = false
    }
}

inline fun <reified T : Throwable> Throwable.findCause(): T? {
    if (this is T) return this

    var th = cause
    while (th != null) {
        if (th is T) return th
        th = th.cause
    }

    return null
}

package app.vimusic.android.utils

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec

class PlaybackRetryManager(
    retryDelaysMs: LongArray
) {
    private val forceFreshResolveIds = HashSet<String>()
    private val retryController = PlaybackRetryController(retryDelaysMs)

    fun reset(mediaId: String) {
        retryController.reset(mediaId)
        synchronized(forceFreshResolveIds) {
            forceFreshResolveIds.remove(mediaId)
        }
    }

    fun nextRetryDelayOrNull(mediaId: String, error: PlaybackException): Long? =
        retryController.nextPlanOrNull(mediaId, error)?.delayMs

    fun markForceFreshResolve(mediaId: String) {
        synchronized(forceFreshResolveIds) {
            forceFreshResolveIds.add(mediaId)
        }
    }

    fun prepareRetry(mediaId: String, invalidateUriCache: () -> Unit) {
        markForceFreshResolve(mediaId)
        invalidateUriCache()
    }

    fun consumeForceFreshResolve(mediaId: String): Boolean =
        synchronized(forceFreshResolveIds) { forceFreshResolveIds.remove(mediaId) }
}

@OptIn(UnstableApi::class)
fun DataSpec.withFreshConnectionHeaders() = withAdditionalHeaders(
    mapOf(
        "Connection" to "close",
        "Cache-Control" to "no-cache"
    )
)

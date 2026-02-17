package app.vimusic.android.utils

import androidx.media3.common.PlaybackException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

data class PlaybackRetryPlan(val delayMs: Long)

class PlaybackRetryController(
    private val retryDelaysMs: LongArray
) {
    private val retryCounts = HashMap<String, Int>()

    fun reset(mediaId: String) {
        retryCounts[mediaId] = 0
    }

    fun nextPlanOrNull(mediaId: String, error: PlaybackException): PlaybackRetryPlan? {
        if (!error.isRecoverablePlaybackError()) return null

        val retryIndex = retryCounts[mediaId] ?: 0
        if (retryIndex >= retryDelaysMs.size) return null

        retryCounts[mediaId] = retryIndex + 1
        return PlaybackRetryPlan(delayMs = retryDelaysMs[retryIndex])
    }
}

fun PlaybackException.isDnsResolutionError(): Boolean =
    findCause<UnknownHostException>() != null ||
        findCause<UnresolvedAddressException>() != null

fun PlaybackException.isRecoverablePlaybackError(): Boolean {
    if (isDnsResolutionError()) return true
    if (errorCode != PlaybackException.ERROR_CODE_UNSPECIFIED) return false
    val detail = message ?: return false
    return detail.contains("Unknown playback error", ignoreCase = true)
}

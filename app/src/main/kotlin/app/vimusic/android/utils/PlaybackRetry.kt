package app.vimusic.android.utils

import app.vimusic.android.service.UnplayableException
import androidx.media3.common.PlaybackException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
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

    if (findCause<UnplayableException>() != null) {
        if (findCause<ParsingException>() != null) return true
        if (findCause<ExtractionException>() != null) return true
        if (findCause<ContentNotAvailableException>() != null) return false
    }

    if (errorCode != PlaybackException.ERROR_CODE_UNSPECIFIED) return false
    val detail = message ?: return false
    return detail.contains("Unknown playback error", ignoreCase = true)
}

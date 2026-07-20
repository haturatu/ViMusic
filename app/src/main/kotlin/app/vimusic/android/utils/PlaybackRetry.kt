package app.vimusic.android.utils

import app.vimusic.android.service.UnplayableException
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
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
    if (findCause<ContentNotAvailableException>() != null) return false
    if (isDnsResolutionError()) return true

    findCause<InvalidResponseCodeException>()?.responseCode?.let { status ->
        return status == HTTP_FORBIDDEN ||
            status == HTTP_REQUEST_TIMEOUT ||
            status == HTTP_RANGE_NOT_SATISFIABLE ||
            status == HTTP_TOO_MANY_REQUESTS ||
            status >= HTTP_INTERNAL_SERVER_ERROR
    }

    if (findCause<ParsingException>() != null || findCause<ExtractionException>() != null) return true
    if (findCause<UnplayableException>() != null) return true

    return when (errorCode) {
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> true

        PlaybackException.ERROR_CODE_UNSPECIFIED ->
            message?.contains("Unknown playback error", ignoreCase = true) == true

        else -> false
    }
}

private const val HTTP_FORBIDDEN = 403
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_RANGE_NOT_SATISFIABLE = 416
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_INTERNAL_SERVER_ERROR = 500

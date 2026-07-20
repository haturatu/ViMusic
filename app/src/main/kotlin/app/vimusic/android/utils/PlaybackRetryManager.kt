package app.vimusic.android.utils

import androidx.media3.common.PlaybackException

class PlaybackRetryManager(
    retryDelaysMs: LongArray
) {
    private val retryController = PlaybackRetryController(retryDelaysMs)

    fun reset(mediaId: String) {
        retryController.reset(mediaId)
    }

    fun nextRetryDelayOrNull(mediaId: String, error: PlaybackException): Long? =
        retryController.nextPlanOrNull(mediaId, error)?.delayMs

    fun prepareRetry(invalidateResolvedStream: () -> Unit) = invalidateResolvedStream()
}

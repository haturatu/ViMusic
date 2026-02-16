package app.vimusic.android.ui.modifiers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import app.vimusic.android.Database
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.AppearancePreferences
import app.vimusic.android.service.isLocal
import app.vimusic.android.transaction
import app.vimusic.android.utils.addNext

@Composable
fun Modifier.songSwipeActions(
    key: Any,
    mediaItem: MediaItem,
    songToHide: Song? = null,
    requireUnconsumed: Boolean = true,
    onSwipeLeftRequested: ((Song) -> Unit)? = null,
    onHideSong: (Song) -> Unit = { song -> transaction { Database.delete(song) } }
): Modifier {
    val binder = LocalPlayerServiceBinder.current
    val canSwipeLeft = AppearancePreferences.swipeToHideSong &&
        songToHide != null &&
        onSwipeLeftRequested != null

    if (!canSwipeLeft && !AppearancePreferences.swipeRightToPlayNext) {
        return this
    }

    return this.swipeToAction(
        key = key,
        requireUnconsumed = requireUnconsumed,
        enableSwipeLeft = canSwipeLeft,
        enableSwipeRight = AppearancePreferences.swipeRightToPlayNext,
        onSwipeLeft = { animationJob ->
            if (canSwipeLeft) {
                val song = requireNotNull(songToHide)
                if (AppearancePreferences.swipeToHideSongConfirm) {
                    requireNotNull(onSwipeLeftRequested).invoke(song)
                } else {
                    if (!song.isLocal) binder?.cache?.removeResource(song.id)
                    onHideSong(song)
                }
            }
            animationJob.join()
        },
        onSwipeRight = { animationJob ->
            binder?.player?.addNext(mediaItem)
            animationJob.join()
        }
    )
}

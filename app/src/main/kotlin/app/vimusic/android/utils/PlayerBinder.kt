package app.vimusic.android.utils

import androidx.media3.common.MediaItem
import app.vimusic.android.service.PlayerService

fun PlayerService.Binder?.playMediaItem(mediaItem: MediaItem) {
    if (this == null) return
    stopRadio()
    player.forcePlay(mediaItem)
}

fun PlayerService.Binder?.playSongAtIndex(
    mediaItems: List<MediaItem>,
    index: Int
) {
    if (this == null) return
    stopRadio()
    player.forcePlayAtIndex(mediaItems, index)
}

fun PlayerService.Binder?.shufflePlay(mediaItems: List<MediaItem>) {
    if (this == null || mediaItems.isEmpty()) return
    stopRadio()
    player.forcePlayFromBeginning(mediaItems.shuffled())
}

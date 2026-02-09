package app.vimusic.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import app.vimusic.android.service.PlayerService

interface PlaybackActions {
    fun play(mediaItem: MediaItem)
    fun playAtIndex(items: List<MediaItem>, index: Int)
    fun shufflePlay(items: List<MediaItem>)
    fun enqueue(items: List<MediaItem>)

    object Empty : PlaybackActions {
        override fun play(mediaItem: MediaItem) = Unit
        override fun playAtIndex(items: List<MediaItem>, index: Int) = Unit
        override fun shufflePlay(items: List<MediaItem>) = Unit
        override fun enqueue(items: List<MediaItem>) = Unit
    }
}

val LocalPlaybackActions = staticCompositionLocalOf<PlaybackActions> { PlaybackActions.Empty }

@Composable
fun rememberPlaybackActions(binder: PlayerService.Binder?): PlaybackActions = remember(binder) {
    object : PlaybackActions {
        override fun play(mediaItem: MediaItem) {
            binder.playMediaItem(mediaItem)
        }

        override fun playAtIndex(items: List<MediaItem>, index: Int) {
            binder.playSongAtIndex(items, index)
        }

        override fun shufflePlay(items: List<MediaItem>) {
            binder.shufflePlay(items)
        }

        override fun enqueue(items: List<MediaItem>) {
            binder?.enqueue(items)
        }
    }
}

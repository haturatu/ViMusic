package app.vimusic.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import app.vimusic.android.models.Song

interface MediaItemMapper<in T> {
    fun map(item: T): MediaItem?
}

object SongMediaItemMapper : MediaItemMapper<Song> {
    override fun map(item: Song): MediaItem = item.asMediaItem
}

@Composable
fun <T> rememberMediaItems(
    items: List<T>,
    mapper: MediaItemMapper<T>
): List<MediaItem> = remember(items) {
    items.mapNotNull(mapper::map)
}

@Composable
fun rememberMediaItems(songs: List<Song>): List<MediaItem> = rememberMediaItems(songs, SongMediaItemMapper)

@Composable
fun <T> rememberMediaItemsOrNull(
    items: List<T>?,
    mapper: MediaItemMapper<T>
): List<MediaItem>? = remember(items) {
    items?.mapNotNull(mapper::map)
}

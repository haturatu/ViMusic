package app.vimusic.android.utils

import androidx.media3.common.MediaItem
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.piped.models.Playlist

object InnertubeSongMediaItemMapper : MediaItemMapper<Innertube.SongItem> {
    override fun map(item: Innertube.SongItem): MediaItem = item.asMediaItem
}

object PipedPlaylistVideoMediaItemMapper : MediaItemMapper<Playlist.Video> {
    override fun map(item: Playlist.Video): MediaItem? = item.asMediaItem
}

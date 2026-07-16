package app.vimusic.android.utils

import androidx.media3.common.MediaItem
import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.piped.models.Playlist

object NewPipeMusicSongMediaItemMapper : MediaItemMapper<NewPipeMusic.SongItem> {
    override fun map(item: NewPipeMusic.SongItem): MediaItem = item.asMediaItem
}

object PipedPlaylistVideoMediaItemMapper : MediaItemMapper<Playlist.Video> {
    override fun map(item: Playlist.Video): MediaItem? = item.asMediaItem
}

package app.vimusic.android.utils

import androidx.media3.common.MediaItem
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.piped.models.Playlist

object YoutubeMusicInnertubeSongMediaItemMapper : MediaItemMapper<YoutubeMusicInnertube.SongItem> {
    override fun map(item: YoutubeMusicInnertube.SongItem): MediaItem = item.asMediaItem
}

object PipedPlaylistVideoMediaItemMapper : MediaItemMapper<Playlist.Video> {
    override fun map(item: Playlist.Video): MediaItem? = item.asMediaItem
}

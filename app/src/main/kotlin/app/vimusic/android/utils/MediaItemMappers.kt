package app.vimusic.android.utils

import androidx.media3.common.MediaItem
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube

object YoutubeMusicInnertubeSongMediaItemMapper : MediaItemMapper<YoutubeMusicInnertube.SongItem> {
    override fun map(item: YoutubeMusicInnertube.SongItem): MediaItem = item.asMediaItem
}

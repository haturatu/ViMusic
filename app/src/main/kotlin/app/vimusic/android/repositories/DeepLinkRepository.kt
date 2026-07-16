package app.vimusic.android.repositories

import androidx.media3.common.MediaItem
import app.vimusic.android.utils.asMediaItem
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.requests.playlistPage
import app.vimusic.providers.youtubemusic.innertube.requests.song

interface DeepLinkRepository {
    suspend fun resolveAlbumBrowseIdFromPlaylist(browseId: String): String?
    suspend fun resolveSongMediaItem(videoId: String): MediaItem?
}

object YoutubeMusicInnertubeDeepLinkRepository : DeepLinkRepository {
    override suspend fun resolveAlbumBrowseIdFromPlaylist(browseId: String): String? =
        YoutubeMusicInnertube.playlistPage(body = BrowseBody(browseId = browseId))
            ?.getOrNull()
            ?.songsPage
            ?.items
            ?.firstOrNull()
            ?.album
            ?.endpoint
            ?.browseId

    override suspend fun resolveSongMediaItem(videoId: String): MediaItem? =
        YoutubeMusicInnertube.song(videoId)?.getOrNull()?.asMediaItem
}

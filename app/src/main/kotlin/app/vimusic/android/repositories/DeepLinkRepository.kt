package app.vimusic.android.repositories

import androidx.media3.common.MediaItem
import app.vimusic.android.utils.asMediaItem
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.BrowseBody
import app.vimusic.providers.innertube.requests.playlistPage
import app.vimusic.providers.innertube.requests.song

interface DeepLinkRepository {
    suspend fun resolveAlbumBrowseIdFromPlaylist(browseId: String): String?
    suspend fun resolveSongMediaItem(videoId: String): MediaItem?
}

object InnertubeDeepLinkRepository : DeepLinkRepository {
    override suspend fun resolveAlbumBrowseIdFromPlaylist(browseId: String): String? =
        Innertube.playlistPage(body = BrowseBody(browseId = browseId))
            ?.getOrNull()
            ?.songsPage
            ?.items
            ?.firstOrNull()
            ?.album
            ?.endpoint
            ?.browseId

    override suspend fun resolveSongMediaItem(videoId: String): MediaItem? =
        Innertube.song(videoId)?.getOrNull()?.asMediaItem
}

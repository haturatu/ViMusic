package app.vimusic.android.repositories

import androidx.media3.common.MediaItem
import app.vimusic.android.utils.asMediaItem
import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.bodies.BrowseBody
import app.vimusic.providers.newpipe.requests.playlistPage
import app.vimusic.providers.newpipe.requests.song

interface DeepLinkRepository {
    suspend fun resolveAlbumBrowseIdFromPlaylist(browseId: String): String?
    suspend fun resolveSongMediaItem(videoId: String): MediaItem?
}

object NewPipeMusicDeepLinkRepository : DeepLinkRepository {
    override suspend fun resolveAlbumBrowseIdFromPlaylist(browseId: String): String? =
        NewPipeMusic.playlistPage(body = BrowseBody(browseId = browseId))
            ?.getOrNull()
            ?.songsPage
            ?.items
            ?.firstOrNull()
            ?.album
            ?.endpoint
            ?.browseId

    override suspend fun resolveSongMediaItem(videoId: String): MediaItem? =
        NewPipeMusic.song(videoId)?.getOrNull()?.asMediaItem
}

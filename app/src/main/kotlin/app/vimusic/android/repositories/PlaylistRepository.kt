package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.SongPlaylistMap
import app.vimusic.android.query
import app.vimusic.android.transaction
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.completed
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.requests.playlistPage

interface PlaylistRepository {
    suspend fun fetchPlaylistPage(
        browseId: String,
        params: String?,
        maxDepth: Int?,
        shouldDedup: Boolean
    ): YoutubeMusicInnertube.PlaylistOrAlbumPage?

    fun importPlaylist(
        name: String,
        browseId: String,
        thumbnailUrl: String?,
        songs: List<YoutubeMusicInnertube.SongItem>?
    )
}

object DatabasePlaylistRepository : PlaylistRepository {
    override suspend fun fetchPlaylistPage(
        browseId: String,
        params: String?,
        maxDepth: Int?,
        shouldDedup: Boolean
    ): YoutubeMusicInnertube.PlaylistOrAlbumPage? =
        YoutubeMusicInnertube
            .playlistPage(BrowseBody(browseId = browseId, params = params))
            ?.completed(maxDepth = maxDepth ?: Int.MAX_VALUE, shouldDedup = shouldDedup)
            ?.getOrNull()

    override fun importPlaylist(
        name: String,
        browseId: String,
        thumbnailUrl: String?,
        songs: List<YoutubeMusicInnertube.SongItem>?
    ) {
        query {
            transaction {
                val playlistId = Database.insert(
                    Playlist(
                        name = name,
                        browseId = browseId,
                        thumbnail = thumbnailUrl
                    )
                )

                songs
                    ?.map(YoutubeMusicInnertube.SongItem::asMediaItem)
                    ?.onEach(Database::insert)
                    ?.mapIndexed { index, mediaItem ->
                        SongPlaylistMap(
                            songId = mediaItem.mediaId,
                            playlistId = playlistId,
                            position = index
                        )
                    }
                    ?.let(Database::insertSongPlaylistMaps)
            }
        }
    }
}

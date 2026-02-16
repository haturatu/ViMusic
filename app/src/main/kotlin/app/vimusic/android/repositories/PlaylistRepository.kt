package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.SongPlaylistMap
import app.vimusic.android.query
import app.vimusic.android.transaction
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.completed
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.BrowseBody
import app.vimusic.providers.innertube.requests.playlistPage

interface PlaylistRepository {
    suspend fun fetchPlaylistPage(
        browseId: String,
        params: String?,
        maxDepth: Int?,
        shouldDedup: Boolean
    ): Innertube.PlaylistOrAlbumPage?

    fun importPlaylist(
        name: String,
        browseId: String,
        thumbnailUrl: String?,
        songs: List<Innertube.SongItem>?
    )
}

object DatabasePlaylistRepository : PlaylistRepository {
    override suspend fun fetchPlaylistPage(
        browseId: String,
        params: String?,
        maxDepth: Int?,
        shouldDedup: Boolean
    ): Innertube.PlaylistOrAlbumPage? =
        Innertube
            .playlistPage(BrowseBody(browseId = browseId, params = params))
            ?.completed(maxDepth = maxDepth ?: Int.MAX_VALUE, shouldDedup = shouldDedup)
            ?.getOrNull()

    override fun importPlaylist(
        name: String,
        browseId: String,
        thumbnailUrl: String?,
        songs: List<Innertube.SongItem>?
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
                    ?.map(Innertube.SongItem::asMediaItem)
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

package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.Song
import app.vimusic.android.models.SongPlaylistMap
import app.vimusic.android.query
import app.vimusic.android.transaction
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.completed
import app.vimusic.providers.innertube.models.bodies.BrowseBody
import app.vimusic.providers.innertube.requests.playlistPage
import app.vimusic.providers.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

interface LocalPlaylistRepository {
    fun observePlaylist(playlistId: Long): Flow<Playlist?>
    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>>
    fun move(playlistId: Long, fromIndex: Int, toIndex: Int)
    fun rename(playlist: Playlist, name: String)
    fun delete(playlist: Playlist)
    suspend fun sync(playlist: Playlist, browseId: String): Result<Unit>
}

object DatabaseLocalPlaylistRepository : LocalPlaylistRepository {
    override fun observePlaylist(playlistId: Long): Flow<Playlist?> = Database.playlist(playlistId)

    override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> = Database.playlistSongs(playlistId)

    override fun move(playlistId: Long, fromIndex: Int, toIndex: Int) {
        transaction { Database.move(playlistId, fromIndex, toIndex) }
    }

    override fun rename(playlist: Playlist, name: String) {
        query { Database.update(playlist.copy(name = name)) }
    }

    override fun delete(playlist: Playlist) {
        query { Database.delete(playlist) }
    }

    override suspend fun sync(playlist: Playlist, browseId: String): Result<Unit> = runCatching {
        Innertube.playlistPage(BrowseBody(browseId = browseId))
            ?.completed()
            ?.getOrNull()
            ?.let { remotePlaylist ->
                transaction {
                    Database.clearPlaylist(playlist.id)

                    remotePlaylist.songsPage
                        ?.items
                        ?.map { it.asMediaItem }
                        ?.onEach(Database::insert)
                        ?.mapIndexed { position, mediaItem ->
                            SongPlaylistMap(
                                songId = mediaItem.mediaId,
                                playlistId = playlist.id,
                                position = position
                            )
                        }
                        ?.let(Database::insertSongPlaylistMaps)
                }
            }
    }.onFailure {
        if (it is CancellationException) throw it
        it.printStackTrace()
    }.map { Unit }
}

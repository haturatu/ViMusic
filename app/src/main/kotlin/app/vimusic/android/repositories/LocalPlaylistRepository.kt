package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.Song
import app.vimusic.android.models.SongPlaylistMap
import app.vimusic.android.query
import app.vimusic.android.transaction
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.completed
import app.vimusic.android.utils.runSuspendCatching
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.requests.playlistPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

interface LocalPlaylistRepository {
    fun observePlaylist(playlistId: Long): Flow<Playlist?>
    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>>
    suspend fun move(playlistId: Long, fromIndex: Int, toIndex: Int)
    suspend fun rename(playlist: Playlist, name: String)
    suspend fun delete(playlist: Playlist)
    suspend fun sync(playlist: Playlist, browseId: String): Result<Unit>
}

object DatabaseLocalPlaylistRepository : LocalPlaylistRepository {
    override fun observePlaylist(playlistId: Long): Flow<Playlist?> = Database.playlist(playlistId)

    override fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> = Database.playlistSongs(playlistId)

    override suspend fun move(playlistId: Long, fromIndex: Int, toIndex: Int) {
        transaction { Database.move(playlistId, fromIndex, toIndex) }
    }

    override suspend fun rename(playlist: Playlist, name: String) {
        query { Database.update(playlist.copy(name = name)) }
    }

    override suspend fun delete(playlist: Playlist) {
        query { Database.delete(playlist) }
    }

    override suspend fun sync(playlist: Playlist, browseId: String): Result<Unit> = runSuspendCatching {
        val initialPage = YoutubeMusicInnertube
            .playlistPage(BrowseBody(browseId = browseId))
            ?: error("Playlist request was cancelled")
        val remotePlaylist = initialPage.completed().getOrThrow()

        transaction {
            // The user can delete the playlist while its remote page is loading.
            // Do not recreate mappings for a parent row that no longer exists.
            if (!Database.playlistExists(playlist.id)) return@transaction

            Database.clearPlaylist(playlist.id)

            remotePlaylist.songsPage
                ?.items
                ?.map { it.asMediaItem }
                ?.onEach { Database.insert(it) }
                ?.mapIndexed { position, mediaItem ->
                    SongPlaylistMap(
                        songId = mediaItem.mediaId,
                        playlistId = playlist.id,
                        position = position
                    )
                }
                ?.let(Database::insertSongPlaylistMaps)
        }
    }.onFailure {
        if (it is CancellationException) throw it
        it.printStackTrace()
    }
}

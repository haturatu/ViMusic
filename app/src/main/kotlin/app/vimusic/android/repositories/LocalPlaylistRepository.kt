package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.internal
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.Song
import app.vimusic.android.models.SongPlaylistMap
import app.vimusic.android.query
import app.vimusic.android.transaction
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.completed
import app.vimusic.android.utils.runSuspendCatching
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.requests.playlistPage
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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

    override suspend fun sync(playlist: Playlist, browseId: String): Result<Unit> = runSuspendCatching {
        val initialPage = YoutubeMusicInnertube
            .playlistPage(BrowseBody(browseId = browseId))
            ?: error("Playlist request was cancelled")
        val remotePlaylist = initialPage.completed().getOrThrow()

        withContext(Dispatchers.IO) {
            Database.internal.runInTransaction {
                // The user can delete the playlist while its remote page is loading.
                // Do not recreate mappings for a parent row that no longer exists.
                if (!Database.playlistExists(playlist.id)) return@runInTransaction

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
    }
}

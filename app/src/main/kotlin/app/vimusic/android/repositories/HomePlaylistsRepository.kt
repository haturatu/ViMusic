package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.PipedSession
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.PlaylistPreview
import app.vimusic.android.query
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.core.data.enums.SortOrder
import app.vimusic.providers.piped.Piped
import app.vimusic.providers.piped.models.PlaylistPreview as PipedPlaylistPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

interface HomePlaylistsRepository {
    fun observePlaylistPreviews(sortBy: PlaylistSortBy, sortOrder: SortOrder): Flow<List<PlaylistPreview>>
    fun observePipedPlaylists(): Flow<Map<PipedSession, List<PipedPlaylistPreview>?>>
    fun createPlaylist(name: String)
}

object DatabaseHomePlaylistsRepository : HomePlaylistsRepository {
    override fun observePlaylistPreviews(
        sortBy: PlaylistSortBy,
        sortOrder: SortOrder
    ): Flow<List<PlaylistPreview>> = Database.playlistPreviews(sortBy = sortBy, sortOrder = sortOrder)

    override fun observePipedPlaylists(): Flow<Map<PipedSession, List<PipedPlaylistPreview>?>> = flow {
        Database.pipedSessions().collect { sessions ->
            emit(
                coroutineScope {
                    sessions.associateWith { session ->
                        async {
                            Piped.playlist.list(session = session.toApiSession())?.getOrNull()
                        }
                    }.mapValues { (_, deferred) -> deferred.await() }
                }
            )
        }
    }

    override fun createPlaylist(name: String) {
        query {
            Database.insert(Playlist(name = name))
        }
    }
}

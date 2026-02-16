package app.vimusic.android.repositories

import app.vimusic.providers.piped.Piped
import app.vimusic.providers.piped.models.Playlist
import app.vimusic.providers.piped.models.Session
import java.util.UUID

interface PipedPlaylistRepository {
    suspend fun fetchPlaylist(session: Session, playlistId: UUID): Playlist?
}

object ApiPipedPlaylistRepository : PipedPlaylistRepository {
    override suspend fun fetchPlaylist(session: Session, playlistId: UUID): Playlist? =
        Piped.playlist.songs(session = session, id = playlistId)?.getOrNull()
}

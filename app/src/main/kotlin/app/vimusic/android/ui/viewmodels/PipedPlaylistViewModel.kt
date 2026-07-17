package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import app.vimusic.android.repositories.PipedPlaylistRepository
import app.vimusic.providers.piped.models.Playlist
import app.vimusic.providers.piped.models.Session
import java.util.UUID

class PipedPlaylistViewModel(
    private val repository: PipedPlaylistRepository
) : ViewModel() {
    suspend fun fetchPlaylist(session: Session, playlistId: UUID): Playlist? =
        repository.fetchPlaylist(session = session, playlistId = playlistId)

    companion object {
        fun factory(repository: PipedPlaylistRepository) = viewModelFactory {
            PipedPlaylistViewModel(repository = repository)
        }
    }
}

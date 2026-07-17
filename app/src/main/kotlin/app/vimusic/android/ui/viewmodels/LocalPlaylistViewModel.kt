package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import app.vimusic.android.models.Playlist
import app.vimusic.android.repositories.LocalPlaylistRepository

class LocalPlaylistViewModel(
    private val repository: LocalPlaylistRepository
) : ViewModel() {
    fun observePlaylist(playlistId: Long) = repository.observePlaylist(playlistId)

    fun observePlaylistSongs(playlistId: Long) = repository.observePlaylistSongs(playlistId)

    fun move(playlistId: Long, fromIndex: Int, toIndex: Int) =
        repository.move(playlistId = playlistId, fromIndex = fromIndex, toIndex = toIndex)

    fun rename(playlist: Playlist, name: String) = repository.rename(playlist = playlist, name = name)

    fun delete(playlist: Playlist) = repository.delete(playlist)

    suspend fun sync(playlist: Playlist, browseId: String) =
        repository.sync(playlist = playlist, browseId = browseId)

    companion object {
        fun factory(repository: LocalPlaylistRepository) = viewModelFactory {
            LocalPlaylistViewModel(repository = repository)
        }
    }
}

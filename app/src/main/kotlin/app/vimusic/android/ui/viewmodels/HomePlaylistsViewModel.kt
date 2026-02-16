package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.repositories.HomePlaylistsRepository
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.core.data.enums.SortOrder

class HomePlaylistsViewModel(
    private val repository: HomePlaylistsRepository
) : ViewModel() {
    fun observePlaylistPreviews(sortBy: PlaylistSortBy, sortOrder: SortOrder) =
        repository.observePlaylistPreviews(sortBy = sortBy, sortOrder = sortOrder)

    fun observePipedPlaylists() = repository.observePipedPlaylists()

    fun createPlaylist(name: String) = repository.createPlaylist(name)

    companion object {
        fun factory(repository: HomePlaylistsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomePlaylistsViewModel(repository = repository) as T
            }
    }
}

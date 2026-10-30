package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vimusic.android.repositories.HomeRepository
import app.vimusic.android.utils.launchDatabaseAction
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.core.data.enums.SortOrder

class HomePlaylistsViewModel(
    private val repository: HomeRepository
) : ViewModel() {
    fun observePlaylistPreviews(sortBy: PlaylistSortBy, sortOrder: SortOrder) =
        repository.observePlaylistPreviews(sortBy = sortBy, sortOrder = sortOrder)

    fun createPlaylist(name: String) {
        viewModelScope.launchDatabaseAction {
            repository.createPlaylist(name)
        }
    }

    companion object {
        fun factory(repository: HomeRepository) = viewModelFactory {
            HomePlaylistsViewModel(repository = repository)
        }
    }
}

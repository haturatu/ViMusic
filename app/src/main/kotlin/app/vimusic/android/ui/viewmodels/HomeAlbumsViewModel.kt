package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.repositories.LibraryRepository
import app.vimusic.core.data.enums.AlbumSortBy
import app.vimusic.core.data.enums.SortOrder

class HomeAlbumsViewModel(
    private val repository: LibraryRepository
) : ViewModel() {
    fun observeAlbums(sortBy: AlbumSortBy, sortOrder: SortOrder) =
        repository.observeAlbums(sortBy = sortBy, sortOrder = sortOrder)

    companion object {
        fun factory(repository: LibraryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeAlbumsViewModel(repository = repository) as T
            }
    }
}

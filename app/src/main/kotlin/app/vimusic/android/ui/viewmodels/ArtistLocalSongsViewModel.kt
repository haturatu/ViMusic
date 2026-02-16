package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.repositories.LibraryRepository

class ArtistLocalSongsViewModel(
    private val repository: LibraryRepository,
    private val browseId: String
) : ViewModel() {
    fun observeSongs() = repository.observeArtistSongs(artistId = browseId)

    companion object {
        fun factory(
            browseId: String,
            repository: LibraryRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ArtistLocalSongsViewModel(repository = repository, browseId = browseId) as T
        }
    }
}

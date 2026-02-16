package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.repositories.SearchResultRepository
import app.vimusic.providers.innertube.Innertube

class SearchResultViewModel(
    private val query: String,
    private val repository: SearchResultRepository
) : ViewModel() {
    suspend fun songsPage(continuation: String?) =
        repository.searchSongs(query = query, continuation = continuation)

    suspend fun albumsPage(continuation: String?) =
        repository.searchAlbums(query = query, continuation = continuation)

    suspend fun artistsPage(continuation: String?) =
        repository.searchArtists(query = query, continuation = continuation)

    suspend fun videosPage(continuation: String?) =
        repository.searchVideos(query = query, continuation = continuation)

    suspend fun playlistsPage(continuation: String?) =
        repository.searchPlaylists(query = query, continuation = continuation)

    companion object {
        fun factory(
            query: String,
            repository: SearchResultRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SearchResultViewModel(query = query, repository = repository) as T
        }
    }
}

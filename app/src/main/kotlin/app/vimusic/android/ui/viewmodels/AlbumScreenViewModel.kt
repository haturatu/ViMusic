package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.models.Album
import app.vimusic.android.repositories.AlbumRepository

class AlbumScreenViewModel(
    private val repository: AlbumRepository
) : ViewModel() {
    fun observeAlbum(browseId: String) = repository.observeAlbum(browseId)

    fun observeAlbumSongs(browseId: String) = repository.observeAlbumSongs(browseId)

    suspend fun fetchAlbumPage(browseId: String) = repository.fetchAlbumPage(browseId)

    fun replaceAlbumFromPage(
        browseId: String,
        bookmarkedAt: Long?,
        page: app.vimusic.providers.innertube.Innertube.PlaylistOrAlbumPage
    ) = repository.replaceAlbumFromPage(
        browseId = browseId,
        bookmarkedAt = bookmarkedAt,
        page = page
    )

    fun toggleBookmark(album: Album?) {
        album ?: return
        repository.updateAlbum(
            album.copy(
                bookmarkedAt = if (album.bookmarkedAt == null) System.currentTimeMillis() else null
            )
        )
    }

    companion object {
        fun factory(repository: AlbumRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AlbumScreenViewModel(repository = repository) as T
            }
    }
}

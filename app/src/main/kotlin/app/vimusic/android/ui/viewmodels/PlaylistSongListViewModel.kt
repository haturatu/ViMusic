package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.repositories.PlaylistRepository
import app.vimusic.providers.innertube.Innertube

class PlaylistSongListViewModel(
    private val repository: PlaylistRepository
) : ViewModel() {
    suspend fun fetchPlaylistPage(
        browseId: String,
        params: String?,
        maxDepth: Int?,
        shouldDedup: Boolean
    ): Innertube.PlaylistOrAlbumPage? = repository.fetchPlaylistPage(
        browseId = browseId,
        params = params,
        maxDepth = maxDepth,
        shouldDedup = shouldDedup
    )

    fun importPlaylist(
        name: String,
        browseId: String,
        thumbnailUrl: String?,
        songs: List<Innertube.SongItem>?
    ) = repository.importPlaylist(
        name = name,
        browseId = browseId,
        thumbnailUrl = thumbnailUrl,
        songs = songs
    )

    companion object {
        fun factory(repository: PlaylistRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaylistSongListViewModel(repository = repository) as T
            }
    }
}

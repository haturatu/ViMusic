package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import app.vimusic.android.models.Playlist
import app.vimusic.android.repositories.QueueRepository
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.core.data.enums.SortOrder

class QueueViewModel(
    private val repository: QueueRepository
) : ViewModel() {
    suspend fun fetchSuggestions(videoId: String): List<MediaItem>? =
        repository.fetchSuggestions(videoId = videoId)

    fun observePlaylistPreviews(sortBy: PlaylistSortBy, sortOrder: SortOrder) =
        repository.observePlaylistPreviews(sortBy = sortBy, sortOrder = sortOrder)

    fun addQueueToPlaylist(playlist: Playlist, index: Int, mediaItems: List<MediaItem>) =
        repository.addQueueToPlaylist(playlist = playlist, index = index, mediaItems = mediaItems)

    companion object {
        fun factory(repository: QueueRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    QueueViewModel(repository = repository) as T
            }
    }
}

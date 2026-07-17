package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import app.vimusic.android.models.Playlist
import app.vimusic.android.repositories.QueueRepository
import app.vimusic.android.ui.state.LoadState
import app.vimusic.android.utils.runSuspendCatching
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.core.data.enums.SortOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QueueViewModel(
    private val repository: QueueRepository
) : ViewModel() {
    private val mutableSuggestions = MutableStateFlow<LoadState<List<MediaItem>>>(LoadState.Idle)
    val suggestions: StateFlow<LoadState<List<MediaItem>>> = mutableSuggestions.asStateFlow()
    private var suggestionsJob: Job? = null

    fun loadSuggestions(videoId: String) {
        suggestionsJob?.cancel()
        mutableSuggestions.value = LoadState.Loading
        suggestionsJob = viewModelScope.launch {
            runSuspendCatching {
                requireNotNull(repository.fetchSuggestions(videoId)) {
                    "Queue suggestions response was empty"
                }
            }.fold(
                onSuccess = { mutableSuggestions.value = LoadState.Content(it) },
                onFailure = { mutableSuggestions.value = LoadState.Error(it) },
            )
        }
    }

    fun clearSuggestions() {
        suggestionsJob?.cancel()
        mutableSuggestions.value = LoadState.Idle
    }

    fun observePlaylistPreviews(sortBy: PlaylistSortBy, sortOrder: SortOrder) =
        repository.observePlaylistPreviews(sortBy = sortBy, sortOrder = sortOrder)

    fun addQueueToPlaylist(playlist: Playlist, index: Int, mediaItems: List<MediaItem>) =
        repository.addQueueToPlaylist(playlist = playlist, index = index, mediaItems = mediaItems)

    companion object {
        fun factory(repository: QueueRepository) = viewModelFactory {
            QueueViewModel(repository = repository)
        }
    }
}

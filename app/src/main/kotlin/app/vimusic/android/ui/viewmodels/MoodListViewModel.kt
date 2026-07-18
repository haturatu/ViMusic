package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vimusic.android.repositories.MoodRepository
import app.vimusic.android.ui.state.LoadState
import app.vimusic.android.utils.requireValue
import app.vimusic.android.utils.runSuspendCatching
import app.vimusic.providers.youtubemusic.innertube.requests.BrowseResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MoodListViewModel(
    private val repository: MoodRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<LoadState<BrowseResult>>(LoadState.Idle)
    val uiState: StateFlow<LoadState<BrowseResult>> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null

    fun load(
        browseId: String,
        params: String?,
        cachedPage: BrowseResult? = null,
        force: Boolean = false,
    ) {
        if (!force && mutableUiState.value is LoadState.Content) return
        if (!force && cachedPage != null) {
            mutableUiState.value = LoadState.Content(cachedPage)
            return
        }
        if (loadJob?.isActive == true) return

        mutableUiState.value = LoadState.Loading
        loadJob = viewModelScope.launch {
            runSuspendCatching {
                repository.fetchMoodPage(browseId, params).requireValue(
                    nullResultMessage = "Mood request was not executed",
                    nullValueMessage = "Mood response was empty",
                ).getOrThrow()
            }.fold(
                onSuccess = { mutableUiState.value = LoadState.Content(it) },
                onFailure = { mutableUiState.value = LoadState.Error(it, cachedPage) },
            )
        }
    }

    companion object {
        fun factory(repository: MoodRepository) = viewModelFactory {
            MoodListViewModel(repository = repository)
        }
    }
}

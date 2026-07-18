package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vimusic.android.repositories.HomeDiscoveryRepository
import app.vimusic.android.ui.state.LoadState
import app.vimusic.android.utils.requireValue
import app.vimusic.android.utils.runSuspendCatching
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeDiscoveryViewModel(
    private val repository: HomeDiscoveryRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<LoadState<YoutubeMusicInnertube.DiscoverPage>>(
        LoadState.Idle
    )
    val uiState: StateFlow<LoadState<YoutubeMusicInnertube.DiscoverPage>> =
        mutableUiState.asStateFlow()
    private var loadJob: Job? = null

    fun load(
        cachedPage: YoutubeMusicInnertube.DiscoverPage? = null,
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
                repository.fetchDiscoverPage().requireValue(
                    nullResultMessage = "Discover request was not executed",
                    nullValueMessage = "Discover response was empty",
                ).getOrThrow()
            }.fold(
                onSuccess = { mutableUiState.value = LoadState.Content(it) },
                onFailure = { mutableUiState.value = LoadState.Error(it, cachedPage) },
            )
        }
    }

    companion object {
        fun factory(repository: HomeDiscoveryRepository) = viewModelFactory {
            HomeDiscoveryViewModel(repository = repository)
        }
    }
}

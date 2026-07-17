package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vimusic.android.models.Song
import app.vimusic.android.repositories.HomeQuickPicksRepository
import app.vimusic.android.utils.requireValue
import app.vimusic.android.utils.runSuspendCatching
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface QuickPicksUiState {
    data object Loading : QuickPicksUiState
    data class Content(
        val page: YoutubeMusicInnertube.RelatedPage,
        val isRefreshing: Boolean = false,
    ) : QuickPicksUiState
    data class Error(
        val throwable: Throwable,
        val stalePage: YoutubeMusicInnertube.RelatedPage? = null,
    ) : QuickPicksUiState
}

class HomeQuickPicksViewModel(
    private val repository: HomeQuickPicksRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<QuickPicksUiState>(QuickPicksUiState.Loading)
    val uiState: StateFlow<QuickPicksUiState> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null
    private var currentVideoId: String? = null

    fun observeTrendingSong(): Flow<Song?> = repository.observeTrendingSong()

    fun observeLastInteractionSong(): Flow<Song?> = repository.observeLastInteractionSong()

    fun load(videoId: String, force: Boolean = false) {
        if (!force && currentVideoId == videoId && loadJob?.isActive == true) return
        currentVideoId = videoId
        loadJob?.cancel()
        val cached = repository.getCachedQuickPicksIfAvailable()
        mutableUiState.value = if (cached == null) QuickPicksUiState.Loading
        else QuickPicksUiState.Content(cached, isRefreshing = true)
        loadJob = viewModelScope.launch {
            runSuspendCatching {
                repository.fetchRelatedPage(videoId).requireValue(
                    nullResultMessage = "Related page request was not executed",
                    nullValueMessage = "Related page was empty",
                ).getOrThrow()
            }.fold(
                onSuccess = { page ->
                    repository.cacheQuickPicks(page)
                    mutableUiState.value = QuickPicksUiState.Content(page)
                },
                onFailure = { error ->
                    mutableUiState.value = QuickPicksUiState.Error(error, cached)
                },
            )
        }
    }

    fun retry() {
        currentVideoId?.let { load(it, force = true) }
    }

    fun removeFromQuickPicks(songId: String) = repository.clearEventsFor(songId = songId)

    fun clearCachedQuickPicks() = repository.clearCachedQuickPicks()

    companion object {
        fun factory(repository: HomeQuickPicksRepository) = viewModelFactory {
            HomeQuickPicksViewModel(repository = repository)
        }
    }
}

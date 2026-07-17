package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vimusic.android.models.Song
import app.vimusic.android.repositories.HomeQuickPicksRepository
import app.vimusic.android.ui.state.LoadState
import app.vimusic.android.utils.requireValue
import app.vimusic.android.utils.runSuspendCatching
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeQuickPicksViewModel(
    private val repository: HomeQuickPicksRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<LoadState<YoutubeMusicInnertube.RelatedPage>>(
        LoadState.Loading
    )
    val uiState: StateFlow<LoadState<YoutubeMusicInnertube.RelatedPage>> =
        mutableUiState.asStateFlow()
    private var loadJob: Job? = null
    private var currentVideoId: String? = null

    fun observeTrendingSong(): Flow<Song?> = repository.observeTrendingSong()

    fun observeLastInteractionSong(): Flow<Song?> = repository.observeLastInteractionSong()

    fun load(videoId: String, force: Boolean = false) {
        if (!force && currentVideoId == videoId && loadJob?.isActive == true) return
        currentVideoId = videoId
        loadJob?.cancel()
        val cached = repository.getCachedQuickPicksIfAvailable()
        mutableUiState.value = if (cached == null) LoadState.Loading else LoadState.Content(cached)
        loadJob = viewModelScope.launch {
            runSuspendCatching {
                repository.fetchRelatedPage(videoId).requireValue(
                    nullResultMessage = "Related page request was not executed",
                    nullValueMessage = "Related page was empty",
                ).getOrThrow()
            }.fold(
                onSuccess = { page ->
                    repository.cacheQuickPicks(page)
                    mutableUiState.value = LoadState.Content(page)
                },
                onFailure = { error ->
                    mutableUiState.value = LoadState.Error(error, cached)
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

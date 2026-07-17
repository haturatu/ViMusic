package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vimusic.android.models.SearchQuery
import app.vimusic.android.repositories.OnlineSearchRepository
import app.vimusic.android.ui.state.LoadState
import app.vimusic.android.utils.requireValue
import app.vimusic.android.utils.runSuspendCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnlineSearchViewModel(
    private val repository: OnlineSearchRepository
) : ViewModel() {
    private val mutableSuggestions = MutableStateFlow<LoadState<List<String>>>(LoadState.Idle)
    val suggestions: StateFlow<LoadState<List<String>>> = mutableSuggestions.asStateFlow()
    private var suggestionsJob: Job? = null

    fun observeHistory(input: String) = repository.observeHistory(input)
    fun observeHistoryCount() = repository.observeHistoryCount()

    fun loadSuggestions(input: String) {
        suggestionsJob?.cancel()
        if (input.isBlank()) {
            mutableSuggestions.value = LoadState.Idle
            return
        }

        suggestionsJob = viewModelScope.launch {
            mutableSuggestions.value = LoadState.Loading
            delay(500)
            runSuspendCatching {
                repository.fetchSuggestions(input).requireValue(
                    nullResultMessage = "Suggestions request was not executed",
                    nullValueMessage = "Suggestions response was empty",
                ).getOrThrow()
            }.fold(
                onSuccess = { mutableSuggestions.value = LoadState.Content(it) },
                onFailure = { mutableSuggestions.value = LoadState.Error(it) },
            )
        }
    }

    fun deleteHistory(searchQuery: SearchQuery) = repository.deleteHistory(searchQuery)
    fun clearHistory() = repository.clearHistory()
    fun saveHistory(text: String) = repository.saveHistory(text)

    companion object {
        fun factory(repository: OnlineSearchRepository) = viewModelFactory {
            OnlineSearchViewModel(repository = repository)
        }
    }
}

package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import app.vimusic.android.models.SearchQuery
import app.vimusic.android.repositories.OnlineSearchRepository

class OnlineSearchViewModel(
    private val repository: OnlineSearchRepository
) : ViewModel() {
    fun observeHistory(input: String) = repository.observeHistory(input)
    fun observeHistoryCount() = repository.observeHistoryCount()

    suspend fun fetchSuggestions(input: String) = repository.fetchSuggestions(input)

    fun deleteHistory(searchQuery: SearchQuery) = repository.deleteHistory(searchQuery)
    fun clearHistory() = repository.clearHistory()
    fun saveHistory(text: String) = repository.saveHistory(text)

    companion object {
        fun factory(repository: OnlineSearchRepository) = viewModelFactory {
            OnlineSearchViewModel(repository = repository)
        }
    }
}

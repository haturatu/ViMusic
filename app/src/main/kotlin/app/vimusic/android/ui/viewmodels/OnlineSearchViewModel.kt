package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.models.SearchQuery
import app.vimusic.android.repositories.OnlineSearchRepository

class OnlineSearchViewModel(
    private val repository: OnlineSearchRepository
) : ViewModel() {
    fun observeHistory(input: String) = repository.observeHistory(input)

    suspend fun fetchSuggestions(input: String) = repository.fetchSuggestions(input)

    fun deleteHistory(searchQuery: SearchQuery) = repository.deleteHistory(searchQuery)

    companion object {
        fun factory(repository: OnlineSearchRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OnlineSearchViewModel(repository = repository) as T
            }
    }
}

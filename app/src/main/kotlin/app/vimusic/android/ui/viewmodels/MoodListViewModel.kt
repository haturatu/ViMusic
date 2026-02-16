package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.repositories.MoodRepository

class MoodListViewModel(
    private val repository: MoodRepository
) : ViewModel() {
    suspend fun fetchMoodPage(browseId: String, params: String?) =
        repository.fetchMoodPage(browseId = browseId, params = params)

    companion object {
        fun factory(repository: MoodRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MoodListViewModel(repository = repository) as T
            }
    }
}

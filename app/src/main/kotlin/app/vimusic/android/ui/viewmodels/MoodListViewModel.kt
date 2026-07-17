package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import app.vimusic.android.repositories.MoodRepository

class MoodListViewModel(
    private val repository: MoodRepository
) : ViewModel() {
    suspend fun fetchMoodPage(browseId: String, params: String?) =
        repository.fetchMoodPage(browseId = browseId, params = params)

    companion object {
        fun factory(repository: MoodRepository) = viewModelFactory {
            MoodListViewModel(repository = repository)
        }
    }
}

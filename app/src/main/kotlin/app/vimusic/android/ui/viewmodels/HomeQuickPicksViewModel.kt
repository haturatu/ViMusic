package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.models.Song
import app.vimusic.android.repositories.HomeQuickPicksRepository
import kotlinx.coroutines.flow.Flow

class HomeQuickPicksViewModel(
    private val repository: HomeQuickPicksRepository
) : ViewModel() {
    fun observeTrendingSong(): Flow<Song?> = repository.observeTrendingSong()

    fun observeLastInteractionSong(): Flow<Song?> = repository.observeLastInteractionSong()

    suspend fun fetchRelatedPage(videoId: String) = repository.fetchRelatedPage(videoId = videoId)

    fun removeFromQuickPicks(songId: String) = repository.clearEventsFor(songId = songId)

    companion object {
        fun factory(repository: HomeQuickPicksRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeQuickPicksViewModel(repository = repository) as T
            }
    }
}

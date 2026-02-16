package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.models.Song
import app.vimusic.android.repositories.HomeQuickPicksRepository
import app.vimusic.providers.innertube.Innertube
import kotlinx.coroutines.flow.Flow

class HomeQuickPicksViewModel(
    private val repository: HomeQuickPicksRepository
) : ViewModel() {
    fun observeTrendingSong(): Flow<Song?> = repository.observeTrendingSong()

    fun observeLastInteractionSong(): Flow<Song?> = repository.observeLastInteractionSong()

    suspend fun fetchRelatedPage(videoId: String) = repository.fetchRelatedPage(videoId = videoId)

    fun removeFromQuickPicks(songId: String) = repository.clearEventsFor(songId = songId)

    fun getCachedQuickPicksIfAvailable(): Innertube.RelatedPage? = repository.getCachedQuickPicksIfAvailable()

    fun cacheQuickPicks(page: Innertube.RelatedPage) = repository.cacheQuickPicks(page = page)

    fun clearCachedQuickPicks() = repository.clearCachedQuickPicks()

    companion object {
        fun factory(repository: HomeQuickPicksRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeQuickPicksViewModel(repository = repository) as T
            }
    }
}

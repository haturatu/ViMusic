package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import app.vimusic.android.repositories.HomeDiscoveryRepository

class HomeDiscoveryViewModel(
    private val repository: HomeDiscoveryRepository
) : ViewModel() {
    suspend fun fetchDiscoverPage() = repository.fetchDiscoverPage()

    companion object {
        fun factory(repository: HomeDiscoveryRepository) = viewModelFactory {
            HomeDiscoveryViewModel(repository = repository)
        }
    }
}

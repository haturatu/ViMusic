package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.repositories.HomeDiscoveryRepository

class HomeDiscoveryViewModel(
    private val repository: HomeDiscoveryRepository
) : ViewModel() {
    suspend fun fetchDiscoverPage() = repository.fetchDiscoverPage()

    companion object {
        fun factory(repository: HomeDiscoveryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeDiscoveryViewModel(repository = repository) as T
            }
    }
}

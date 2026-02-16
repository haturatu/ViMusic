package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.models.PipedSession
import app.vimusic.android.repositories.SyncSettingsRepository
import app.vimusic.providers.piped.models.Instance
import io.ktor.http.Url

class SyncSettingsViewModel(
    private val repository: SyncSettingsRepository
) : ViewModel() {
    fun observePipedSessions() = repository.observePipedSessions()

    suspend fun fetchInstances(): Result<List<Instance>>? = repository.fetchInstances()

    suspend fun login(apiBaseUrl: Url, username: String, password: String) =
        repository.login(apiBaseUrl = apiBaseUrl, username = username, password = password)

    fun saveSession(session: app.vimusic.providers.piped.models.Session, username: String) =
        repository.saveSession(session = session, username = username)

    fun deleteSession(session: PipedSession) = repository.deleteSession(session)

    companion object {
        fun factory(repository: SyncSettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SyncSettingsViewModel(repository = repository) as T
            }
    }
}

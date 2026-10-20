package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vimusic.android.models.PipedSession
import app.vimusic.android.repositories.SyncSettingsRepository
import app.vimusic.android.utils.launchDatabaseAction
import app.vimusic.providers.piped.models.Instance
import io.ktor.http.Url

class SyncSettingsViewModel(
    private val repository: SyncSettingsRepository
) : ViewModel() {
    fun observePipedSessions() = repository.observePipedSessions()

    suspend fun fetchInstances(): Result<List<Instance>>? = repository.fetchInstances()

    suspend fun login(apiBaseUrl: Url, username: String, password: String) =
        repository.login(apiBaseUrl = apiBaseUrl, username = username, password = password)

    suspend fun saveSession(session: app.vimusic.providers.piped.models.Session, username: String) =
        repository.saveSession(session = session, username = username)

    fun deleteSession(session: PipedSession) {
        viewModelScope.launchDatabaseAction {
            repository.deleteSession(session)
        }
    }

    companion object {
        fun factory(repository: SyncSettingsRepository) = viewModelFactory {
            SyncSettingsViewModel(repository = repository)
        }
    }
}

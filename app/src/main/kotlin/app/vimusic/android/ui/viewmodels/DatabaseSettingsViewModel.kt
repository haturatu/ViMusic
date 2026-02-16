package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.vimusic.android.repositories.DatabaseSettingsRepository
import java.io.InputStream
import java.io.OutputStream

class DatabaseSettingsViewModel(
    private val repository: DatabaseSettingsRepository
) : ViewModel() {
    fun observeEventsCount() = repository.observeEventsCount()
    fun observeBlacklistLength() = repository.observeBlacklistLength()
    fun clearEvents() = repository.clearEvents()
    fun resetBlacklist() = repository.resetBlacklist()
    fun backupTo(output: OutputStream) = repository.backupTo(output)
    fun restoreFrom(input: InputStream) = repository.restoreFrom(input)

    companion object {
        fun factory(repository: DatabaseSettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DatabaseSettingsViewModel(repository = repository) as T
            }
    }
}

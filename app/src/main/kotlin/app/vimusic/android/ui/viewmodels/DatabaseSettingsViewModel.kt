package app.vimusic.android.ui.viewmodels

import androidx.lifecycle.ViewModel
import app.vimusic.android.repositories.DatabaseSettingsRepository
import java.io.InputStream
import java.io.OutputStream

class DatabaseSettingsViewModel(
    private val repository: DatabaseSettingsRepository
) : ViewModel() {
    fun observeBlacklistLength() = repository.observeBlacklistLength()
    fun resetBlacklist() = repository.resetBlacklist()
    suspend fun backupTo(output: OutputStream) = repository.backupTo(output)
    suspend fun restoreFrom(input: InputStream) = repository.restoreFrom(input)

    companion object {
        fun factory(repository: DatabaseSettingsRepository) = viewModelFactory {
            DatabaseSettingsViewModel(repository = repository)
        }
    }
}

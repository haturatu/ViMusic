package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.dbPath
import app.vimusic.android.internal
import app.vimusic.android.query
import app.vimusic.android.transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

interface DatabaseSettingsRepository {
    fun observeEventsCount(): Flow<Int>
    fun observeBlacklistLength(): Flow<Int>
    fun clearEvents()
    fun resetBlacklist()
    suspend fun backupTo(output: OutputStream)
    suspend fun restoreFrom(input: InputStream)
}

object DefaultDatabaseSettingsRepository : DatabaseSettingsRepository {
    override fun observeEventsCount(): Flow<Int> = Database.eventsCount().distinctUntilChanged()

    override fun observeBlacklistLength(): Flow<Int> = Database.blacklistLength().distinctUntilChanged()

    override fun clearEvents() {
        query(Database::clearEvents)
    }

    override fun resetBlacklist() {
        transaction { Database.resetBlacklist() }
    }

    override suspend fun backupTo(output: OutputStream) {
        Database.checkpoint()
        val path = requireNotNull(Database.internal.dbPath) { "Database path is null" }
        FileInputStream(path).use { input -> input.copyTo(output) }
    }

    override suspend fun restoreFrom(input: InputStream) {
        Database.checkpoint()
        val path = requireNotNull(Database.internal.dbPath) { "Database path is null" }
        Database.internal.close()
        FileOutputStream(path).use { output -> input.copyTo(output) }
    }
}

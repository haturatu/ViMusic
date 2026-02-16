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
    fun backupTo(output: OutputStream)
    fun restoreFrom(input: InputStream)
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

    override fun backupTo(output: OutputStream) {
        query {
            Database.checkpoint()
            FileInputStream(Database.internal.dbPath).use { input -> input.copyTo(output) }
        }
    }

    override fun restoreFrom(input: InputStream) {
        query {
            Database.checkpoint()
            Database.internal.close()
            FileOutputStream(Database.internal.dbPath).use { output -> input.copyTo(output) }
        }
    }
}

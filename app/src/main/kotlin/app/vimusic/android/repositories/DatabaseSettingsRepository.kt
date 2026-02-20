package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.dbPath
import app.vimusic.android.internal
import app.vimusic.android.query
import app.vimusic.android.transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        withContext(Dispatchers.IO) {
            val path = requireNotNull(Database.internal.dbPath) { "Database path is null" }
            val tmpBackup = File.createTempFile("vimusic_backup_", ".db")

            try {
                runCatching {
                    Database.checkpoint()
                    val escapedPath = tmpBackup.absolutePath.replace("'", "''")
                    Database.internal.openHelper.writableDatabase.execSQL("VACUUM INTO '$escapedPath'")
                }.getOrElse {
                    // Fallback for SQLite engines where VACUUM INTO is unavailable.
                    FileInputStream(path).use { input ->
                        FileOutputStream(tmpBackup).use(input::copyTo)
                    }
                }

                FileInputStream(tmpBackup).use { input -> input.copyTo(output) }
            } finally {
                tmpBackup.delete()
            }
        }
    }

    override suspend fun restoreFrom(input: InputStream) {
        val path = requireNotNull(Database.internal.dbPath) { "Database path is null" }
        val dbFile = File(path)
        val tempFile = File("$path.restore_tmp")
        val backupFile = File("$path.restore_bak")

        // Read restore source first; if this fails, current database remains untouched.
        FileOutputStream(tempFile).use { output -> input.copyTo(output) }

        Database.checkpoint()
        FileInputStream(dbFile).use { current -> FileOutputStream(backupFile).use(current::copyTo) }
        Database.internal.close()

        try {
            runCatching {
                FileInputStream(tempFile).use { restored ->
                    FileOutputStream(dbFile).use(restored::copyTo)
                }
            }.onFailure { error ->
                runCatching {
                    FileInputStream(backupFile).use { backup ->
                        FileOutputStream(dbFile).use(backup::copyTo)
                    }
                }
                runCatching { Database.internal.openHelper.writableDatabase }
                throw error
            }.onSuccess {
                backupFile.delete()
            }
        } finally {
            tempFile.delete()
        }
    }
}

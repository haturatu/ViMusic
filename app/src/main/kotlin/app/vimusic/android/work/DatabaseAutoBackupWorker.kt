package app.vimusic.android.work

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vimusic.android.Database
import app.vimusic.android.dbPath
import app.vimusic.android.internal
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.repositories.DefaultDatabaseSettingsRepository
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class DatabaseAutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val WORK_NAME = "database_auto_backup_worker"
        private const val BACKUP_MIME_TYPE = "application/vnd.sqlite3"
        private const val BACKUP_FILE_NAME = "ViMusic_auto_backup.db"

        fun upsert(
            context: Context,
            enabled: Boolean = DataPreferences.autoDatabaseBackupEnabled,
            treeUri: String = DataPreferences.autoDatabaseBackupTreeUri
        ) = runCatching {
            val workManager = WorkManager.getInstance(context)

            if (!enabled || treeUri.isBlank()) {
                workManager.cancelUniqueWork(WORK_NAME)
                return@runCatching
            }

            val request = PeriodicWorkRequestBuilder<DatabaseAutoBackupWorker>(
                15,
                TimeUnit.MINUTES
            ).setConstraints(
                Constraints(
                    requiresBatteryNotLow = true,
                    requiresStorageNotLow = true
                )
            ).build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            )
        }.also { it.exceptionOrNull()?.printStackTrace() }
    }

    override suspend fun doWork(): Result {
        val treeUriValue = DataPreferences.autoDatabaseBackupTreeUri
        if (treeUriValue.isBlank()) return Result.failure()
        val previousHash = DataPreferences.autoDatabaseBackupLastSha256

        val currentHash = runCatching {
            Database.checkpoint()
            val path = requireNotNull(Database.internal.dbPath) { "Database path is null" }
            sha256(path)
        }.getOrElse {
            it.printStackTrace()
            return Result.retry()
        }

        if (
            previousHash.isNotBlank() &&
            previousHash == currentHash &&
            DataPreferences.autoDatabaseBackupDocumentUri.isNotBlank()
        ) {
            return Result.success()
        }

        val resolver = applicationContext.contentResolver
        val treeUri = Uri.parse(treeUriValue)
        val parentUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
        }.getOrElse {
            it.printStackTrace()
            return Result.failure()
        }

        val documentUri = runCatching {
            resolveBackupDocumentUri(treeUri, parentUri)
        }.getOrElse {
            it.printStackTrace()
            return Result.retry()
        } ?: return Result.retry()

        DataPreferences.autoDatabaseBackupDocumentUri = documentUri.toString()

        return try {
            requireNotNull(resolver.openOutputStream(documentUri)) {
                "Failed to open output stream for backup"
            }.use { output ->
                DefaultDatabaseSettingsRepository.backupTo(output)
            }
            DataPreferences.autoDatabaseBackupLastSha256 = currentHash
            Result.success()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            Result.retry()
        }
    }

    private fun resolveBackupDocumentUri(treeUri: Uri, parentUri: Uri): Uri? {
        val resolver = applicationContext.contentResolver

        DataPreferences.autoDatabaseBackupDocumentUri
            .takeIf(String::isNotBlank)
            ?.let(Uri::parse)
            ?.let { savedUri ->
                if (resolver.openOutputStream(savedUri) != null) return savedUri
            }

        findExistingBackupDocumentUri(treeUri)?.let { existing ->
            return existing
        }

        return DocumentsContract.createDocument(
            resolver,
            parentUri,
            BACKUP_MIME_TYPE,
            BACKUP_FILE_NAME
        )
    }

    private fun findExistingBackupDocumentUri(treeUri: Uri): Uri? {
        val resolver = applicationContext.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)

        resolver.query(
            childrenUri,
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            if (idIndex < 0 || nameIndex < 0) return null

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (name != BACKUP_FILE_NAME) continue

                val documentId = cursor.getString(idIndex) ?: continue
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            }
        }

        return null
    }

    private fun sha256(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        FileInputStream(path).use { input ->
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

@file:Suppress("TooGenericExceptionCaught") // Storage providers can throw implementation-specific failures.

package app.vimusic.android.work

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.utils.withDatabaseSnapshot
import kotlinx.coroutines.CancellationException
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class DatabaseAutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "DatabaseAutoBackup"
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
        }.onFailure { error -> Log.e(TAG, "Failed to schedule database backup", error) }
    }

    override suspend fun doWork(): Result {
        val treeUriValue = DataPreferences.autoDatabaseBackupTreeUri
        if (treeUriValue.isBlank()) return Result.failure()
        val previousHash = DataPreferences.autoDatabaseBackupLastSha256

        return try {
            withDatabaseSnapshot { snapshot ->
                val currentHash = sha256(snapshot.absolutePath)
                if (
                    previousHash.isNotBlank() && previousHash == currentHash &&
                    DataPreferences.autoDatabaseBackupDocumentUri.isNotBlank()
                ) return@withDatabaseSnapshot Result.success()

                val treeUri = Uri.parse(treeUriValue)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val documentUri = resolveBackupDocumentUri(treeUri, parentUri)
                    ?: return@withDatabaseSnapshot Result.retry()
                val resolver = applicationContext.contentResolver
                requireNotNull(resolver.openOutputStream(documentUri, "wt")) {
                    "Failed to open output stream for backup"
                }.use { output ->
                    snapshot.inputStream().use { it.copyTo(output) }
                }
                DataPreferences.autoDatabaseBackupDocumentUri = documentUri.toString()
                DataPreferences.autoDatabaseBackupLastSha256 = currentHash
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Failed to create database backup", error)
            Result.retry()
        }
    }

    private fun resolveBackupDocumentUri(treeUri: Uri, parentUri: Uri): Uri? {
        val resolver = applicationContext.contentResolver

        DataPreferences.autoDatabaseBackupDocumentUri
            .takeIf(String::isNotBlank)
            ?.let(Uri::parse)
            ?.let { savedUri ->
                val exists = runCatching {
                    resolver.query(savedUri, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                        ?.use { it.moveToFirst() } == true
                }.getOrDefault(false)
                if (exists) return savedUri
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
                val name = cursor.getString(nameIndex)
                val documentId = cursor.getString(idIndex)
                if (name == BACKUP_FILE_NAME && documentId != null) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                }
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

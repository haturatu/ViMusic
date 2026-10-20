package app.vimusic.android.utils

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.vimusic.android.Database
import app.vimusic.android.dbPath
import app.vimusic.android.internal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

internal suspend fun <T> withDatabaseSnapshot(block: suspend (File) -> T): T = withContext(Dispatchers.IO) {
    val path = requireNotNull(Database.internal.dbPath) { "Database path is null" }
    val snapshot = File.createTempFile("vimusic_backup_", ".db")
    try {
        createDatabaseSnapshot(path, snapshot)
        block(snapshot)
    } finally {
        snapshot.delete()
    }
}

internal suspend fun createDatabaseSnapshot(path: String, snapshot: File, forceLegacy: Boolean = false) {
    // This connection belongs only to the backup, so Room's writer remains available.
    SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { source ->
        val version = source.rawQuery("SELECT sqlite_version()", null).use {
            check(it.moveToFirst())
            it.getString(0).split('.').map(String::toInt)
        }
        if (!forceLegacy && (version[0] > 3 || version[0] == 3 && version[1] >= 27)) {
            source.execSQL("VACUUM INTO ?", arrayOf(snapshot.absolutePath))
        } else {
            copyDatabaseSnapshot(source, snapshot)
        }
    }
    coroutineContext.ensureActive()
}

private data class SnapshotSchema(val type: String, val name: String, val sql: String)

private suspend fun copyDatabaseSnapshot(source: SQLiteDatabase, snapshot: File) {
    // SAVEPOINT starts a deferred read transaction, including on older Android SQLite engines.
    // It avoids Android's beginTransaction(), which would reserve the source writer.
    source.execSQL("SAVEPOINT vimusic_backup")
    try {
        val schema = source.rawQuery(
            "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY type, name",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(SnapshotSchema(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                }
            }
        }
        val virtualTables = schema.filter { it.sql.startsWith("CREATE VIRTUAL TABLE", ignoreCase = true) }
        val tables = schema.filter { entry ->
            entry.type == "table" && !entry.name.startsWith("sqlite_") &&
                entry !in virtualTables && virtualTables.none { entry.name.startsWith("${it.name}_") }
        }
        SQLiteDatabase.openOrCreateDatabase(snapshot, null).use { target ->
            target.beginTransaction()
            try {
                target.execSQL("DROP TABLE IF EXISTS android_metadata")
                tables.forEach { target.execSQL(it.sql) }
                tables.forEach { copySnapshotTable(source, target, it.name) }
                virtualTables.forEach {
                    target.execSQL(it.sql)
                    val name = quoteIdentifier(it.name)
                    target.execSQL("INSERT INTO $name ($name) VALUES ('rebuild')")
                }
                // Preserve AUTOINCREMENT counters, including IDs of deleted rows.
                if (schema.any { it.name == "sqlite_sequence" }) {
                    target.execSQL("DELETE FROM sqlite_sequence")
                    copySnapshotTable(source, target, "sqlite_sequence")
                }
                schema.filter { it.type == "index" || it.type == "view" || it.type == "trigger" }
                    .forEach { target.execSQL(it.sql) }
                target.version = source.version
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
        }
    } finally {
        source.execSQL("RELEASE SAVEPOINT vimusic_backup")
    }
}

private suspend fun copySnapshotTable(source: SQLiteDatabase, target: SQLiteDatabase, table: String) {
    val name = quoteIdentifier(table)
    source.rawQuery("SELECT rowid, * FROM $name", null).use { rows ->
        val columns = listOf("rowid") + rows.columnNames.drop(1)
        val sql = "INSERT INTO $name (${columns.joinToString(",", transform = ::quoteIdentifier)}) " +
            "VALUES (${columns.joinToString(",") { "?" }})"
        target.compileStatement(sql).use { statement ->
            while (rows.moveToNext()) {
                coroutineContext.ensureActive()
                statement.clearBindings()
                columns.indices.forEach { index ->
                    when (rows.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> statement.bindNull(index + 1)
                        Cursor.FIELD_TYPE_INTEGER -> statement.bindLong(index + 1, rows.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> statement.bindDouble(index + 1, rows.getDouble(index))
                        Cursor.FIELD_TYPE_BLOB -> statement.bindBlob(index + 1, rows.getBlob(index))
                        else -> statement.bindString(index + 1, rows.getString(index))
                    }
                }
                statement.executeInsert()
            }
        }
    }
}

private fun quoteIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

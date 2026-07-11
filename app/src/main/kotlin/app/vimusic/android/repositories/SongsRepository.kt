package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Song
import app.vimusic.android.transaction
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface SongsRepository {
    fun pagedSongs(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        isLocal: Boolean = false,
        onlyPlayed: Boolean = false,
        excludeZeroDuration: Boolean = false,
        searchQuery: String? = null
    ): Flow<PagingData<Song>>

    suspend fun songs(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        isLocal: Boolean = false,
        onlyPlayed: Boolean = false,
        excludeZeroDuration: Boolean = false
    ): List<Song>

    fun deleteSong(song: Song)

    fun upsertSongs(songs: List<Song>)
}

object DatabaseSongsRepository : SongsRepository {
    override fun pagedSongs(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        isLocal: Boolean,
        onlyPlayed: Boolean,
        excludeZeroDuration: Boolean,
        searchQuery: String?
    ): Flow<PagingData<Song>> = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = false)
    ) {
        searchQuery?.takeIf(String::isNotBlank)?.let { query ->
            Database.search(
                query = query.toFtsPrefixQuery(),
                isLocal = isLocal,
                onlyPlayed = onlyPlayed,
                excludeZeroDuration = excludeZeroDuration
            )
        } ?: Database.pagedSongs(
            sortBy = sortBy,
            sortOrder = sortOrder,
            isLocal = isLocal,
            onlyPlayed = onlyPlayed,
            excludeZeroDuration = excludeZeroDuration
        )
    }.flow

    override suspend fun songs(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        isLocal: Boolean,
        onlyPlayed: Boolean,
        excludeZeroDuration: Boolean
    ): List<Song> = Database.songs(sortBy, sortOrder, isLocal).first().filter { song ->
            (!onlyPlayed || song.totalPlayTimeMs > 0L) &&
                    (!excludeZeroDuration || song.durationText != "0:00")
        }

    override fun deleteSong(song: Song) {
        transaction { Database.delete(song) }
    }

    override fun upsertSongs(songs: List<Song>) {
        transaction { songs.forEach(Database::insert) }
    }
}

fun String.toFtsPrefixQuery(): String =
    trim().split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" AND ") { token ->
        "\"${token.replace("\"", "\"\"")}\"*"
    }

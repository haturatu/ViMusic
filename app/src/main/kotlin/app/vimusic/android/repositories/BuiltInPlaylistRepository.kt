package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Song
import app.vimusic.android.models.SongWithContentLength
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder
import kotlinx.coroutines.flow.Flow

interface BuiltInPlaylistRepository {
    fun observeFavorites(sortBy: SongSortBy, sortOrder: SortOrder): Flow<List<Song>>
    fun observeOffline(sortBy: SongSortBy, sortOrder: SortOrder): Flow<List<SongWithContentLength>>
    fun observeTop(periodMillis: Long?, limit: Int): Flow<List<Song>>
    fun observeHistory(): Flow<List<Song>>
}

object DatabaseBuiltInPlaylistRepository : BuiltInPlaylistRepository {
    override fun observeFavorites(sortBy: SongSortBy, sortOrder: SortOrder): Flow<List<Song>> =
        Database.favorites(sortBy = sortBy, sortOrder = sortOrder)

    override fun observeOffline(
        sortBy: SongSortBy,
        sortOrder: SortOrder
    ): Flow<List<SongWithContentLength>> = Database.songsWithContentLength(sortBy = sortBy, sortOrder = sortOrder)

    override fun observeTop(periodMillis: Long?, limit: Int): Flow<List<Song>> =
        if (periodMillis == null) {
            Database.songsByPlayTimeDesc(limit = limit)
        } else {
            Database.trending(limit = limit, period = periodMillis)
        }

    override fun observeHistory(): Flow<List<Song>> = Database.history()
}

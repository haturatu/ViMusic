package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Song
import app.vimusic.android.transaction
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder
import kotlinx.coroutines.flow.Flow

interface SongsRepository {
    fun observeSongs(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        isLocal: Boolean = false
    ): Flow<List<Song>>

    fun deleteSong(song: Song)

    fun upsertSongs(songs: List<Song>)
}

object DatabaseSongsRepository : SongsRepository {
    override fun observeSongs(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        isLocal: Boolean
    ): Flow<List<Song>> = Database.songs(sortBy = sortBy, sortOrder = sortOrder, isLocal = isLocal)

    override fun deleteSong(song: Song) {
        transaction { Database.delete(song) }
    }

    override fun upsertSongs(songs: List<Song>) {
        transaction { songs.forEach(Database::insert) }
    }
}

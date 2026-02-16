package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Album
import app.vimusic.android.models.PlaylistPreview
import app.vimusic.android.models.Song
import app.vimusic.android.models.SongWithContentLength
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface MediaLibraryRepository {
    suspend fun getRecentSongs(limit: Int): List<Song>
    suspend fun getPlaylistPreviewsByDateAddedDesc(): List<PlaylistPreview>
    suspend fun getAlbumsByRowIdDesc(): List<Album>
    suspend fun getFavoritesShuffled(): List<Song>
    suspend fun getOfflineCachedShuffled(isCached: (SongWithContentLength) -> Boolean): List<Song>
    suspend fun getTopSongs(durationMillis: Long?, length: Int): List<Song>
    suspend fun getLocalSongs(sortBy: SongSortBy, sortOrder: SortOrder): List<Song>
    suspend fun getPlaylistSongsShuffled(playlistId: Long): List<Song>?
    suspend fun getAlbumSongs(albumId: String): List<Song>?
}

object DatabaseMediaLibraryRepository : MediaLibraryRepository {
    override suspend fun getRecentSongs(limit: Int): List<Song> =
        Database.songsByPlayTimeDesc(limit = limit).first()

    override suspend fun getPlaylistPreviewsByDateAddedDesc(): List<PlaylistPreview> =
        Database.playlistPreviewsByDateAddedDesc().first()

    override suspend fun getAlbumsByRowIdDesc(): List<Album> =
        Database.albumsByRowIdDesc().first()

    override suspend fun getFavoritesShuffled(): List<Song> =
        Database.favorites().first().shuffled()

    override suspend fun getOfflineCachedShuffled(
        isCached: (SongWithContentLength) -> Boolean
    ): List<Song> = Database
        .songsWithContentLength()
        .first()
        .filter(isCached)
        .map(SongWithContentLength::song)
        .shuffled()

    override suspend fun getTopSongs(durationMillis: Long?, length: Int): List<Song> {
        val flow = if (durationMillis != null) {
            Database.trending(limit = length, period = durationMillis)
        } else {
            Database.songsByPlayTimeDesc(limit = length)
                .distinctUntilChanged()
                .cancellable()
        }
        return flow.first()
    }

    override suspend fun getLocalSongs(sortBy: SongSortBy, sortOrder: SortOrder): List<Song> =
        Database
            .songs(sortBy = sortBy, sortOrder = sortOrder, isLocal = true)
            .map { songs -> songs.filter { it.durationText != "0:00" } }
            .first()

    override suspend fun getPlaylistSongsShuffled(playlistId: Long): List<Song>? =
        Database.playlistWithSongs(playlistId).first()?.songs?.shuffled()

    override suspend fun getAlbumSongs(albumId: String): List<Song>? =
        Database.albumSongs(albumId).first()
}

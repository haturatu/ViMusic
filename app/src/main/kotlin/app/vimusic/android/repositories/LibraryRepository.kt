package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Album
import app.vimusic.android.models.Song
import app.vimusic.core.data.enums.AlbumSortBy
import app.vimusic.core.data.enums.SortOrder
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeAlbums(sortBy: AlbumSortBy, sortOrder: SortOrder): Flow<List<Album>>
    fun observeArtistSongs(artistId: String): Flow<List<Song>>
}

object DatabaseLibraryRepository : LibraryRepository {
    override fun observeAlbums(sortBy: AlbumSortBy, sortOrder: SortOrder): Flow<List<Album>> =
        Database.albums(sortBy = sortBy, sortOrder = sortOrder)

    override fun observeArtistSongs(artistId: String): Flow<List<Song>> =
        Database.artistSongs(artistId)
}

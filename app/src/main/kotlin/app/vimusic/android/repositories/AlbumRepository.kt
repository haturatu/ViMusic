package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Album
import app.vimusic.android.models.Song
import app.vimusic.android.query
import app.vimusic.android.models.SongAlbumMap
import app.vimusic.android.transaction
import app.vimusic.android.utils.asMediaItem
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.BrowseBody
import app.vimusic.providers.innertube.requests.albumPage
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun observeAlbum(browseId: String): Flow<Album?>
    fun observeAlbumSongs(browseId: String): Flow<List<Song>>
    suspend fun fetchAlbumPage(browseId: String): Result<Innertube.PlaylistOrAlbumPage?>?
    fun replaceAlbumFromPage(
        browseId: String,
        bookmarkedAt: Long?,
        page: Innertube.PlaylistOrAlbumPage
    )

    fun updateAlbum(album: Album)
}

object DatabaseAlbumRepository : AlbumRepository {
    override fun observeAlbum(browseId: String): Flow<Album?> = Database.album(browseId)

    override fun observeAlbumSongs(browseId: String): Flow<List<Song>> = Database.albumSongs(browseId)

    override suspend fun fetchAlbumPage(browseId: String): Result<Innertube.PlaylistOrAlbumPage?>? =
        Innertube.albumPage(BrowseBody(browseId = browseId))

    override fun replaceAlbumFromPage(
        browseId: String,
        bookmarkedAt: Long?,
        page: Innertube.PlaylistOrAlbumPage
    ) {
        val songAlbumMaps = page
            .songsPage
            ?.items
            ?.map { it.asMediaItem }
            ?.onEach(Database::insert)
            ?.mapIndexed { position, mediaItem ->
                SongAlbumMap(
                    songId = mediaItem.mediaId,
                    albumId = browseId,
                    position = position
                )
            }
            ?: emptyList()

        transaction {
            Database.clearAlbum(browseId)
            Database.upsert(
                album = Album(
                    id = browseId,
                    title = page.title,
                    description = page.description,
                    thumbnailUrl = page.thumbnail?.url,
                    year = page.year,
                    authorsText = page.authors?.joinToString("") { it.name.orEmpty() },
                    shareUrl = page.url,
                    timestamp = System.currentTimeMillis(),
                    bookmarkedAt = bookmarkedAt,
                    otherInfo = page.otherInfo
                ),
                songAlbumMaps = songAlbumMaps
            )
        }
    }

    override fun updateAlbum(album: Album) {
        query { Database.update(album) }
    }
}

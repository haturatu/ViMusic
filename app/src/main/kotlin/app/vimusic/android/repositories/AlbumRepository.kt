package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Album
import app.vimusic.android.models.Song
import app.vimusic.android.models.SongAlbumMap
import app.vimusic.android.query
import app.vimusic.android.transaction
import app.vimusic.android.utils.asMediaItem
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.requests.albumPage
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun observeAlbum(browseId: String): Flow<Album?>
    fun observeAlbumSongs(browseId: String): Flow<List<Song>>
    suspend fun fetchAlbumPage(browseId: String): Result<YoutubeMusicInnertube.PlaylistOrAlbumPage?>?
    suspend fun replaceAlbumFromPage(
        browseId: String,
        bookmarkedAt: Long?,
        page: YoutubeMusicInnertube.PlaylistOrAlbumPage
    )

    suspend fun updateAlbum(album: Album)
}

object DatabaseAlbumRepository : AlbumRepository {
    override fun observeAlbum(browseId: String): Flow<Album?> = Database.album(browseId)

    override fun observeAlbumSongs(browseId: String): Flow<List<Song>> = Database.albumSongs(browseId)

    override suspend fun fetchAlbumPage(browseId: String): Result<YoutubeMusicInnertube.PlaylistOrAlbumPage?>? =
        YoutubeMusicInnertube.albumPage(BrowseBody(browseId = browseId))

    override suspend fun replaceAlbumFromPage(
        browseId: String,
        bookmarkedAt: Long?,
        page: YoutubeMusicInnertube.PlaylistOrAlbumPage
    ) {
        transaction {
            val songAlbumMaps = page
                .songsPage
                ?.items
                ?.map { it.asMediaItem }
                ?.onEach { Database.insert(it) }
                ?.mapIndexed { position, mediaItem ->
                    SongAlbumMap(
                        songId = mediaItem.mediaId,
                        albumId = browseId,
                        position = position
                    )
                }
                ?: emptyList()

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

    override suspend fun updateAlbum(album: Album) {
        query { Database.update(album) }
    }
}

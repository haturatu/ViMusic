package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Artist
import app.vimusic.android.query
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.youtubemusic.innertube.requests.artistPage
import app.vimusic.providers.youtubemusic.innertube.requests.itemsPage
import app.vimusic.providers.youtubemusic.innertube.utils.from
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun observeArtist(browseId: String): Flow<Artist?>

    suspend fun fetchArtistPage(browseId: String): Result<YoutubeMusicInnertube.ArtistPage?>?

    fun upsertArtist(artist: Artist)
    fun updateArtist(artist: Artist)

    suspend fun artistSongsPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.SongItem>?>

    suspend fun artistAlbumsPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.AlbumItem>?>

    suspend fun artistSinglesPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.AlbumItem>?>
}

object DatabaseArtistRepository : ArtistRepository {
    override fun observeArtist(browseId: String): Flow<Artist?> = Database.artist(browseId)

    override suspend fun fetchArtistPage(browseId: String) =
        YoutubeMusicInnertube.artistPage(BrowseBody(browseId = browseId))

    override fun upsertArtist(artist: Artist) {
        query { Database.upsert(artist) }
    }

    override fun updateArtist(artist: Artist) {
        query { Database.update(artist) }
    }

    override suspend fun artistSongsPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.SongItem>?> {
        return continuation?.let {
            YoutubeMusicInnertube.itemsPage(
                body = ContinuationBody(continuation = continuation),
                fromMusicResponsiveListItemRenderer = YoutubeMusicInnertube.SongItem::from
            )
        } ?: artistPage
            ?.songsEndpoint
            ?.takeIf { it.browseId != null }
            ?.let { endpoint ->
                YoutubeMusicInnertube.itemsPage(
                    body = BrowseBody(
                        browseId = endpoint.browseId!!,
                        params = endpoint.params
                    ),
                    fromMusicResponsiveListItemRenderer = YoutubeMusicInnertube.SongItem::from
                )
            }
            ?: Result.success(
                YoutubeMusicInnertube.ItemsPage(
                    items = artistPage?.songs,
                    continuation = null
                )
            )
    }

    override suspend fun artistAlbumsPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.AlbumItem>?> {
        return continuation?.let {
            YoutubeMusicInnertube.itemsPage(
                body = ContinuationBody(continuation = continuation),
                fromMusicTwoRowItemRenderer = YoutubeMusicInnertube.AlbumItem::from
            )
        } ?: artistPage
            ?.albumsEndpoint
            ?.takeIf { it.browseId != null }
            ?.let { endpoint ->
                YoutubeMusicInnertube.itemsPage(
                    body = BrowseBody(
                        browseId = endpoint.browseId!!,
                        params = endpoint.params
                    ),
                    fromMusicTwoRowItemRenderer = YoutubeMusicInnertube.AlbumItem::from
                )
            }
            ?: Result.success(
                YoutubeMusicInnertube.ItemsPage(
                    items = artistPage?.albums,
                    continuation = null
                )
            )
    }

    override suspend fun artistSinglesPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.AlbumItem>?> {
        return continuation?.let {
            YoutubeMusicInnertube.itemsPage(
                body = ContinuationBody(continuation = continuation),
                fromMusicTwoRowItemRenderer = YoutubeMusicInnertube.AlbumItem::from
            )
        } ?: artistPage
            ?.singlesEndpoint
            ?.takeIf { it.browseId != null }
            ?.let { endpoint ->
                YoutubeMusicInnertube.itemsPage(
                    body = BrowseBody(
                        browseId = endpoint.browseId!!,
                        params = endpoint.params
                    ),
                    fromMusicTwoRowItemRenderer = YoutubeMusicInnertube.AlbumItem::from
                )
            }
            ?: Result.success(
                YoutubeMusicInnertube.ItemsPage(
                    items = artistPage?.singles,
                    continuation = null
                )
            )
    }
}

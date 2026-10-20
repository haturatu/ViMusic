package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Artist
import app.vimusic.android.query
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.MusicResponsiveListItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicTwoRowItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.NavigationEndpoint
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.youtubemusic.innertube.requests.artistPage
import app.vimusic.providers.youtubemusic.innertube.requests.itemsPage
import app.vimusic.providers.youtubemusic.innertube.utils.from
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun observeArtist(browseId: String): Flow<Artist?>

    suspend fun fetchArtistPage(browseId: String): Result<YoutubeMusicInnertube.ArtistPage?>?

    suspend fun upsertArtist(artist: Artist)
    suspend fun updateArtist(artist: Artist)

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

    suspend fun artistVideosPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?,
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.VideoItem>?>
}

object DatabaseArtistRepository : ArtistRepository {
    override fun observeArtist(browseId: String): Flow<Artist?> = Database.artist(browseId)

    override suspend fun fetchArtistPage(browseId: String) =
        YoutubeMusicInnertube.artistPage(BrowseBody(browseId = browseId))

    override suspend fun upsertArtist(artist: Artist) {
        query { Database.upsert(artist) }
    }

    override suspend fun updateArtist(artist: Artist) {
        query { Database.update(artist) }
    }

    override suspend fun artistSongsPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ) = loadArtistItems(
        endpoint = artistPage?.songsEndpoint,
        continuation = continuation,
        initialItems = artistPage?.songs,
        fromList = YoutubeMusicInnertube.SongItem::from
    )

    override suspend fun artistAlbumsPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ) = loadArtistItems(
        endpoint = artistPage?.albumsEndpoint,
        continuation = continuation,
        initialItems = artistPage?.albums,
        fromGrid = YoutubeMusicInnertube.AlbumItem::from
    )

    override suspend fun artistSinglesPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ) = loadArtistItems(
        endpoint = artistPage?.singlesEndpoint,
        continuation = continuation,
        initialItems = artistPage?.singles,
        fromGrid = YoutubeMusicInnertube.AlbumItem::from
    )

    override suspend fun artistVideosPage(
        artistPage: YoutubeMusicInnertube.ArtistPage?,
        continuation: String?
    ) = loadArtistItems(
        endpoint = artistPage?.videosEndpoint,
        continuation = continuation,
        initialItems = artistPage?.videos,
        fromList = YoutubeMusicInnertube.VideoItem::from
    )

    private suspend fun <T : YoutubeMusicInnertube.Item> loadArtistItems(
        endpoint: NavigationEndpoint.Endpoint.Browse?,
        continuation: String?,
        initialItems: List<T>?,
        fromList: (MusicResponsiveListItemRenderer) -> T? = { null },
        fromGrid: (MusicTwoRowItemRenderer) -> T? = { null }
    ): Result<YoutubeMusicInnertube.ItemsPage<T>?> {
        return continuation?.let {
            YoutubeMusicInnertube.itemsPage(
                body = ContinuationBody(continuation = it),
                fromMusicResponsiveListItemRenderer = fromList,
                fromMusicTwoRowItemRenderer = fromGrid
            )
        } ?: endpoint?.browseId?.let { browseId ->
            YoutubeMusicInnertube.itemsPage(
                body = BrowseBody(browseId = browseId, params = endpoint.params),
                fromMusicResponsiveListItemRenderer = fromList,
                fromMusicTwoRowItemRenderer = fromGrid
            )
        } ?: Result.success(
            YoutubeMusicInnertube.ItemsPage(items = initialItems, continuation = null)
        )
    }
}

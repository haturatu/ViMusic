package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Artist
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.BrowseBody
import app.vimusic.providers.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.innertube.requests.artistPage
import app.vimusic.providers.innertube.requests.itemsPage
import app.vimusic.providers.innertube.utils.from
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun observeArtist(browseId: String): Flow<Artist?>

    suspend fun fetchArtistPage(browseId: String): Result<Innertube.ArtistPage?>?

    fun upsertArtist(artist: Artist)
    fun updateArtist(artist: Artist)

    suspend fun artistSongsPage(
        artistPage: Innertube.ArtistPage?,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.SongItem>?>

    suspend fun artistAlbumsPage(
        artistPage: Innertube.ArtistPage?,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.AlbumItem>?>

    suspend fun artistSinglesPage(
        artistPage: Innertube.ArtistPage?,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.AlbumItem>?>
}

object DatabaseArtistRepository : ArtistRepository {
    override fun observeArtist(browseId: String): Flow<Artist?> = Database.artist(browseId)

    override suspend fun fetchArtistPage(browseId: String) =
        Innertube.artistPage(BrowseBody(browseId = browseId))

    override fun upsertArtist(artist: Artist) {
        Database.upsert(artist)
    }

    override fun updateArtist(artist: Artist) {
        Database.update(artist)
    }

    override suspend fun artistSongsPage(
        artistPage: Innertube.ArtistPage?,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.SongItem>?> {
        return continuation?.let {
            Innertube.itemsPage(
                body = ContinuationBody(continuation = continuation),
                fromMusicResponsiveListItemRenderer = Innertube.SongItem::from
            )
        } ?: artistPage
            ?.songsEndpoint
            ?.takeIf { it.browseId != null }
            ?.let { endpoint ->
                Innertube.itemsPage(
                    body = BrowseBody(
                        browseId = endpoint.browseId!!,
                        params = endpoint.params
                    ),
                    fromMusicResponsiveListItemRenderer = Innertube.SongItem::from
                )
            }
            ?: Result.success(
                Innertube.ItemsPage(
                    items = artistPage?.songs,
                    continuation = null
                )
            )
    }

    override suspend fun artistAlbumsPage(
        artistPage: Innertube.ArtistPage?,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.AlbumItem>?> {
        return continuation?.let {
            Innertube.itemsPage(
                body = ContinuationBody(continuation = continuation),
                fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from
            )
        } ?: artistPage
            ?.albumsEndpoint
            ?.takeIf { it.browseId != null }
            ?.let { endpoint ->
                Innertube.itemsPage(
                    body = BrowseBody(
                        browseId = endpoint.browseId!!,
                        params = endpoint.params
                    ),
                    fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from
                )
            }
            ?: Result.success(
                Innertube.ItemsPage(
                    items = artistPage?.albums,
                    continuation = null
                )
            )
    }

    override suspend fun artistSinglesPage(
        artistPage: Innertube.ArtistPage?,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.AlbumItem>?> {
        return continuation?.let {
            Innertube.itemsPage(
                body = ContinuationBody(continuation = continuation),
                fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from
            )
        } ?: artistPage
            ?.singlesEndpoint
            ?.takeIf { it.browseId != null }
            ?.let { endpoint ->
                Innertube.itemsPage(
                    body = BrowseBody(
                        browseId = endpoint.browseId!!,
                        params = endpoint.params
                    ),
                    fromMusicTwoRowItemRenderer = Innertube.AlbumItem::from
                )
            }
            ?: Result.success(
                Innertube.ItemsPage(
                    items = artistPage?.singles,
                    continuation = null
                )
            )
    }
}

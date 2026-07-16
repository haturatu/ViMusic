package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Artist
import app.vimusic.android.query
import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.bodies.BrowseBody
import app.vimusic.providers.newpipe.models.bodies.ContinuationBody
import app.vimusic.providers.newpipe.requests.artistPage
import app.vimusic.providers.newpipe.requests.itemsPage
import app.vimusic.providers.newpipe.utils.from
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun observeArtist(browseId: String): Flow<Artist?>

    suspend fun fetchArtistPage(browseId: String): Result<NewPipeMusic.ArtistPage?>?

    fun upsertArtist(artist: Artist)
    fun updateArtist(artist: Artist)

    suspend fun artistSongsPage(
        artistPage: NewPipeMusic.ArtistPage?,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.SongItem>?>

    suspend fun artistAlbumsPage(
        artistPage: NewPipeMusic.ArtistPage?,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.AlbumItem>?>

    suspend fun artistSinglesPage(
        artistPage: NewPipeMusic.ArtistPage?,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.AlbumItem>?>
}

object DatabaseArtistRepository : ArtistRepository {
    override fun observeArtist(browseId: String): Flow<Artist?> = Database.artist(browseId)

    override suspend fun fetchArtistPage(browseId: String) =
        NewPipeMusic.artistPage(BrowseBody(browseId = browseId))

    override fun upsertArtist(artist: Artist) {
        query { Database.upsert(artist) }
    }

    override fun updateArtist(artist: Artist) {
        query { Database.update(artist) }
    }

    override suspend fun artistSongsPage(
        artistPage: NewPipeMusic.ArtistPage?,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.SongItem>?> {
        return continuation?.let {
            NewPipeMusic.itemsPage(
                body = ContinuationBody(continuation = continuation),
                fromMusicResponsiveListItemRenderer = NewPipeMusic.SongItem::from
            )
        } ?: artistPage
            ?.songsEndpoint
            ?.takeIf { it.browseId != null }
            ?.let { endpoint ->
                NewPipeMusic.itemsPage(
                    body = BrowseBody(
                        browseId = endpoint.browseId!!,
                        params = endpoint.params
                    ),
                    fromMusicResponsiveListItemRenderer = NewPipeMusic.SongItem::from
                )
            }
            ?: Result.success(
                NewPipeMusic.ItemsPage(
                    items = artistPage?.songs,
                    continuation = null
                )
            )
    }

    override suspend fun artistAlbumsPage(
        artistPage: NewPipeMusic.ArtistPage?,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.AlbumItem>?> {
        return continuation?.let {
            NewPipeMusic.itemsPage(
                body = ContinuationBody(continuation = continuation),
                fromMusicTwoRowItemRenderer = NewPipeMusic.AlbumItem::from
            )
        } ?: artistPage
            ?.albumsEndpoint
            ?.takeIf { it.browseId != null }
            ?.let { endpoint ->
                NewPipeMusic.itemsPage(
                    body = BrowseBody(
                        browseId = endpoint.browseId!!,
                        params = endpoint.params
                    ),
                    fromMusicTwoRowItemRenderer = NewPipeMusic.AlbumItem::from
                )
            }
            ?: Result.success(
                NewPipeMusic.ItemsPage(
                    items = artistPage?.albums,
                    continuation = null
                )
            )
    }

    override suspend fun artistSinglesPage(
        artistPage: NewPipeMusic.ArtistPage?,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.AlbumItem>?> {
        return continuation?.let {
            NewPipeMusic.itemsPage(
                body = ContinuationBody(continuation = continuation),
                fromMusicTwoRowItemRenderer = NewPipeMusic.AlbumItem::from
            )
        } ?: artistPage
            ?.singlesEndpoint
            ?.takeIf { it.browseId != null }
            ?.let { endpoint ->
                NewPipeMusic.itemsPage(
                    body = BrowseBody(
                        browseId = endpoint.browseId!!,
                        params = endpoint.params
                    ),
                    fromMusicTwoRowItemRenderer = NewPipeMusic.AlbumItem::from
                )
            }
            ?: Result.success(
                NewPipeMusic.ItemsPage(
                    items = artistPage?.singles,
                    continuation = null
                )
            )
    }
}

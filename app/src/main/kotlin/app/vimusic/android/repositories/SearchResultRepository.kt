package app.vimusic.android.repositories

import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.innertube.models.bodies.SearchBody
import app.vimusic.providers.innertube.requests.searchPage
import app.vimusic.providers.innertube.utils.from

interface SearchResultRepository {
    suspend fun searchSongs(
        query: String,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.SongItem>?>?

    suspend fun searchAlbums(
        query: String,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.AlbumItem>?>?

    suspend fun searchArtists(
        query: String,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.ArtistItem>?>?

    suspend fun searchVideos(
        query: String,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.VideoItem>?>?

    suspend fun searchPlaylists(
        query: String,
        continuation: String?
    ): Result<Innertube.ItemsPage<Innertube.PlaylistItem>?>?
}

object InnertubeSearchResultRepository : SearchResultRepository {
    override suspend fun searchSongs(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        Innertube.searchPage(
            body = SearchBody(
                query = query,
                params = Innertube.SearchFilter.Song.value
            ),
            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
        )
    } else {
        Innertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
        )
    }

    override suspend fun searchAlbums(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        Innertube.searchPage(
            body = SearchBody(
                query = query,
                params = Innertube.SearchFilter.Album.value
            ),
            fromMusicShelfRendererContent = Innertube.AlbumItem::from
        )
    } else {
        Innertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = Innertube.AlbumItem::from
        )
    }

    override suspend fun searchArtists(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        Innertube.searchPage(
            body = SearchBody(
                query = query,
                params = Innertube.SearchFilter.Artist.value
            ),
            fromMusicShelfRendererContent = Innertube.ArtistItem::from
        )
    } else {
        Innertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = Innertube.ArtistItem::from
        )
    }

    override suspend fun searchVideos(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        Innertube.searchPage(
            body = SearchBody(
                query = query,
                params = Innertube.SearchFilter.Video.value
            ),
            fromMusicShelfRendererContent = Innertube.VideoItem::from
        )
    } else {
        Innertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = Innertube.VideoItem::from
        )
    }

    override suspend fun searchPlaylists(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        Innertube.searchPage(
            body = SearchBody(
                query = query,
                params = Innertube.SearchFilter.CommunityPlaylist.value
            ),
            fromMusicShelfRendererContent = Innertube.PlaylistItem::from
        )
    } else {
        Innertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = Innertube.PlaylistItem::from
        )
    }
}

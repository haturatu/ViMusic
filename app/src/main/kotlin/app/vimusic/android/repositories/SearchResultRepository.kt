package app.vimusic.android.repositories

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.bodies.ContinuationBody
import app.vimusic.providers.newpipe.models.bodies.SearchBody
import app.vimusic.providers.newpipe.requests.searchPage
import app.vimusic.providers.newpipe.utils.from

interface SearchResultRepository {
    suspend fun searchSongs(
        query: String,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.SongItem>?>?

    suspend fun searchAlbums(
        query: String,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.AlbumItem>?>?

    suspend fun searchArtists(
        query: String,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.ArtistItem>?>?

    suspend fun searchVideos(
        query: String,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.VideoItem>?>?

    suspend fun searchPlaylists(
        query: String,
        continuation: String?
    ): Result<NewPipeMusic.ItemsPage<NewPipeMusic.PlaylistItem>?>?
}

object NewPipeMusicSearchResultRepository : SearchResultRepository {
    override suspend fun searchSongs(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        NewPipeMusic.searchPage(
            body = SearchBody(
                query = query,
                params = NewPipeMusic.SearchFilter.Song.value
            ),
            fromMusicShelfRendererContent = NewPipeMusic.SongItem.Companion::from
        )
    } else {
        NewPipeMusic.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = NewPipeMusic.SongItem.Companion::from
        )
    }

    override suspend fun searchAlbums(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        NewPipeMusic.searchPage(
            body = SearchBody(
                query = query,
                params = NewPipeMusic.SearchFilter.Album.value
            ),
            fromMusicShelfRendererContent = NewPipeMusic.AlbumItem::from
        )
    } else {
        NewPipeMusic.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = NewPipeMusic.AlbumItem::from
        )
    }

    override suspend fun searchArtists(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        NewPipeMusic.searchPage(
            body = SearchBody(
                query = query,
                params = NewPipeMusic.SearchFilter.Artist.value
            ),
            fromMusicShelfRendererContent = NewPipeMusic.ArtistItem::from
        )
    } else {
        NewPipeMusic.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = NewPipeMusic.ArtistItem::from
        )
    }

    override suspend fun searchVideos(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        NewPipeMusic.searchPage(
            body = SearchBody(
                query = query,
                params = NewPipeMusic.SearchFilter.Video.value
            ),
            fromMusicShelfRendererContent = NewPipeMusic.VideoItem::from
        )
    } else {
        NewPipeMusic.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = NewPipeMusic.VideoItem::from
        )
    }

    override suspend fun searchPlaylists(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        NewPipeMusic.searchPage(
            body = SearchBody(
                query = query,
                params = NewPipeMusic.SearchFilter.CommunityPlaylist.value
            ),
            fromMusicShelfRendererContent = NewPipeMusic.PlaylistItem::from
        )
    } else {
        NewPipeMusic.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = NewPipeMusic.PlaylistItem::from
        )
    }
}

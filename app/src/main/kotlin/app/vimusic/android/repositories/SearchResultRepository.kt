package app.vimusic.android.repositories

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.SearchBody
import app.vimusic.providers.youtubemusic.innertube.requests.searchPage
import app.vimusic.providers.youtubemusic.innertube.utils.from

interface SearchResultRepository {
    suspend fun searchSongs(
        query: String,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.SongItem>?>?

    suspend fun searchAlbums(
        query: String,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.AlbumItem>?>?

    suspend fun searchArtists(
        query: String,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.ArtistItem>?>?

    suspend fun searchVideos(
        query: String,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.VideoItem>?>?

    suspend fun searchPlaylists(
        query: String,
        continuation: String?
    ): Result<YoutubeMusicInnertube.ItemsPage<YoutubeMusicInnertube.PlaylistItem>?>?
}

object YoutubeMusicInnertubeSearchResultRepository : SearchResultRepository {
    override suspend fun searchSongs(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        YoutubeMusicInnertube.searchPage(
            body = SearchBody(
                query = query,
                params = YoutubeMusicInnertube.SearchFilter.Song.value
            ),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.SongItem.Companion::from
        )
    } else {
        YoutubeMusicInnertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.SongItem.Companion::from
        )
    }

    override suspend fun searchAlbums(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        YoutubeMusicInnertube.searchPage(
            body = SearchBody(
                query = query,
                params = YoutubeMusicInnertube.SearchFilter.Album.value
            ),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.AlbumItem::from
        )
    } else {
        YoutubeMusicInnertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.AlbumItem::from
        )
    }

    override suspend fun searchArtists(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        YoutubeMusicInnertube.searchPage(
            body = SearchBody(
                query = query,
                params = YoutubeMusicInnertube.SearchFilter.Artist.value
            ),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.ArtistItem::from
        )
    } else {
        YoutubeMusicInnertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.ArtistItem::from
        )
    }

    override suspend fun searchVideos(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        YoutubeMusicInnertube.searchPage(
            body = SearchBody(
                query = query,
                params = YoutubeMusicInnertube.SearchFilter.Video.value
            ),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.VideoItem::from
        )
    } else {
        YoutubeMusicInnertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.VideoItem::from
        )
    }

    override suspend fun searchPlaylists(
        query: String,
        continuation: String?
    ) = if (continuation == null) {
        YoutubeMusicInnertube.searchPage(
            body = SearchBody(
                query = query,
                params = YoutubeMusicInnertube.SearchFilter.CommunityPlaylist.value
            ),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.PlaylistItem::from
        )
    } else {
        YoutubeMusicInnertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = YoutubeMusicInnertube.PlaylistItem::from
        )
    }
}

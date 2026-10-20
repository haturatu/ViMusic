package app.vimusic.android.repositories

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.MusicShelfRenderer
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
    override suspend fun searchSongs(query: String, continuation: String?) = search(
        query = query,
        continuation = continuation,
        filter = YoutubeMusicInnertube.SearchFilter.Song,
        mapper = YoutubeMusicInnertube.SongItem::from
    )

    override suspend fun searchAlbums(query: String, continuation: String?) = search(
        query = query,
        continuation = continuation,
        filter = YoutubeMusicInnertube.SearchFilter.Album,
        mapper = YoutubeMusicInnertube.AlbumItem::from
    )

    override suspend fun searchArtists(query: String, continuation: String?) = search(
        query = query,
        continuation = continuation,
        filter = YoutubeMusicInnertube.SearchFilter.Artist,
        mapper = YoutubeMusicInnertube.ArtistItem::from
    )

    override suspend fun searchVideos(query: String, continuation: String?) = search(
        query = query,
        continuation = continuation,
        filter = YoutubeMusicInnertube.SearchFilter.Video,
        mapper = YoutubeMusicInnertube.VideoItem::from
    )

    override suspend fun searchPlaylists(query: String, continuation: String?) = search(
        query = query,
        continuation = continuation,
        filter = YoutubeMusicInnertube.SearchFilter.CommunityPlaylist,
        mapper = YoutubeMusicInnertube.PlaylistItem::from
    )

    private suspend fun <T : YoutubeMusicInnertube.Item> search(
        query: String,
        continuation: String?,
        filter: YoutubeMusicInnertube.SearchFilter,
        mapper: (MusicShelfRenderer.Content) -> T?
    ) = if (continuation == null) {
        YoutubeMusicInnertube.searchPage(
            body = SearchBody(query = query, params = filter.value),
            fromMusicShelfRendererContent = mapper
        )
    } else {
        YoutubeMusicInnertube.searchPage(
            body = ContinuationBody(continuation = continuation),
            fromMusicShelfRendererContent = mapper
        )
    }
}

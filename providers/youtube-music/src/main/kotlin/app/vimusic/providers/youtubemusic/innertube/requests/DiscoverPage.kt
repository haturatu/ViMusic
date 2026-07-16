package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.BrowseResponse
import app.vimusic.providers.youtubemusic.innertube.models.MusicTwoRowItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.models.oddElements
import app.vimusic.providers.youtubemusic.innertube.models.splitBySeparator
import app.vimusic.providers.youtubemusic.innertube.utils.from
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun YoutubeMusicInnertube.discoverPage() = runCatchingCancellable {
    val response = client.post<BrowseResponse>(
        BROWSE,
        BrowseBody(browseId = "FEmusic_explore"),
        fieldMask = "contents",
    )

    val sections = response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents

    YoutubeMusicInnertube.DiscoverPage(
        newReleaseAlbums = sections?.find {
            it.musicCarouselShelfRenderer
                ?.header
                ?.musicCarouselShelfBasicHeaderRenderer
                ?.moreContentButton
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
                ?.browseId == "FEmusic_new_releases_albums"
        }?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull { it.musicTwoRowItemRenderer?.toNewReleaseAlbumPage() }
            .orEmpty(),
        moods = sections?.find {
            it.musicCarouselShelfRenderer
                ?.header
                ?.musicCarouselShelfBasicHeaderRenderer
                ?.moreContentButton
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
                ?.browseId == "FEmusic_moods_and_genres"
        }?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull { it.musicNavigationButtonRenderer?.toMood() }
            .orEmpty(),
        trending = run {
            val renderer = sections?.find {
                it.musicCarouselShelfRenderer
                    ?.header
                    ?.musicCarouselShelfBasicHeaderRenderer
                    ?.moreContentButton
                    ?.buttonRenderer
                    ?.navigationEndpoint
                    ?.browseEndpoint
                    ?.browseEndpointContextSupportedConfigs
                    ?.browseEndpointContextMusicConfig
                    ?.pageType == "MUSIC_PAGE_TYPE_PLAYLIST"
            }?.musicCarouselShelfRenderer

            YoutubeMusicInnertube.DiscoverPage.Trending(
                songs = renderer
                    ?.toBrowseItem(YoutubeMusicInnertube.SongItem::from)
                    ?.items
                    ?.filterIsInstance<YoutubeMusicInnertube.SongItem>()
                    ?.map { song -> // Why, YouTube, why
                        song.copy(
                            authors = song.authors?.firstOrNull()?.let { listOf(it) } ?: emptyList()
                        )
                    }
                    .orEmpty(),
                endpoint = renderer
                    ?.header
                    ?.musicCarouselShelfBasicHeaderRenderer
                    ?.moreContentButton
                    ?.buttonRenderer
                    ?.navigationEndpoint
                    ?.browseEndpoint
            )
        }
    )
}

fun MusicTwoRowItemRenderer.toNewReleaseAlbumPage() = YoutubeMusicInnertube.AlbumItem(
    info = YoutubeMusicInnertube.Info(
        name = title?.text,
        endpoint = navigationEndpoint?.browseEndpoint
    ),
    authors = subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
        YoutubeMusicInnertube.Info(
            name = it.text,
            endpoint = it.navigationEndpoint?.browseEndpoint
        )
    },
    year = subtitle?.runs?.lastOrNull()?.text,
    thumbnail = thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.firstOrNull()
)

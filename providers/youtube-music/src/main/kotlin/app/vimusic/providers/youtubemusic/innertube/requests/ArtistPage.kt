package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.BrowseResponse
import app.vimusic.providers.youtubemusic.innertube.models.Context
import app.vimusic.providers.youtubemusic.innertube.models.MusicCarouselShelfRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicShelfRenderer
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.utils.findSectionByTitle
import app.vimusic.providers.youtubemusic.innertube.utils.findSectionByTitleAny
import app.vimusic.providers.youtubemusic.innertube.utils.from
import app.vimusic.providers.utils.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext

suspend fun YoutubeMusicInnertube.artistPage(body: BrowseBody) = runCatchingCancellable {
    val ctx = currentCoroutineContext()
    val response = client.post<BrowseResponse>(BROWSE, body, fieldMask = "contents,header")

    val responseNoLang by lazy {
        CoroutineScope(ctx).async(start = CoroutineStart.LAZY) {
            client.post<BrowseResponse>(
                BROWSE, body.copy(context = Context.DefaultWebNoLang), fieldMask = "contents,header"
            )
        }
    }

    suspend fun findSectionByTitle(text: String) = response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.get(0)
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.findSectionByTitle(text) ?: responseNoLang.await()
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.get(0)
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.findSectionByTitle(text)

    suspend fun findSectionByTitleAny(vararg texts: String) = response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.get(0)
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.findSectionByTitleAny(*texts) ?: responseNoLang.await()
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.get(0)
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.findSectionByTitleAny(*texts)

    val songsSection = findSectionByTitleAny("Songs", "Top songs")
        ?.musicShelfRenderer
    val albumsSection = findSectionByTitleAny("Albums", "Releases")
        ?.musicCarouselShelfRenderer
    val singlesSection = findSectionByTitleAny("Singles & EPs", "Singles", "EPs")
        ?.musicCarouselShelfRenderer
    val videosSection = findSectionByTitleAny("Videos", "Video", "動画", "视频", "동영상")
        ?.musicCarouselShelfRenderer

    YoutubeMusicInnertube.ArtistPage(
        name = response
            .header
            ?.musicImmersiveHeaderRenderer
            ?.title
            ?.text,
        description = response
            .header
            ?.musicImmersiveHeaderRenderer
            ?.description
            ?.text,
        thumbnail = (
                response
                    .header
                    ?.musicImmersiveHeaderRenderer
                    ?.foregroundThumbnail
                    ?: response
                        .header
                        ?.musicImmersiveHeaderRenderer
                        ?.thumbnail
                )
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.getOrNull(0),
        shuffleEndpoint = response
            .header
            ?.musicImmersiveHeaderRenderer
            ?.playButton
            ?.buttonRenderer
            ?.navigationEndpoint
            ?.watchEndpoint,
        radioEndpoint = response
            .header
            ?.musicImmersiveHeaderRenderer
            ?.startRadioButton
            ?.buttonRenderer
            ?.navigationEndpoint
            ?.watchEndpoint,
        songs = songsSection
            ?.contents
            ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(YoutubeMusicInnertube.SongItem::from),
        songsEndpoint = songsSection
            ?.bottomEndpoint
            ?.browseEndpoint,
        albums = albumsSection
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(YoutubeMusicInnertube.AlbumItem::from),
        albumsEndpoint = albumsSection
            ?.header
            ?.musicCarouselShelfBasicHeaderRenderer
            ?.moreContentButton
            ?.buttonRenderer
            ?.navigationEndpoint
            ?.browseEndpoint,
        singles = singlesSection
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(YoutubeMusicInnertube.AlbumItem::from),
        singlesEndpoint = singlesSection
            ?.header
            ?.musicCarouselShelfBasicHeaderRenderer
            ?.moreContentButton
            ?.buttonRenderer
            ?.navigationEndpoint
            ?.browseEndpoint,
        videos = videosSection
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(YoutubeMusicInnertube.VideoItem::from),
        videosEndpoint = videosSection
            ?.header
            ?.musicCarouselShelfBasicHeaderRenderer
            ?.moreContentButton
            ?.buttonRenderer
            ?.navigationEndpoint
            ?.browseEndpoint,
        subscribersCountText = response
            .header
            ?.musicImmersiveHeaderRenderer
            ?.subscriptionButton
            ?.subscribeButtonRenderer
            ?.subscriberCountText
            ?.text
    )
}

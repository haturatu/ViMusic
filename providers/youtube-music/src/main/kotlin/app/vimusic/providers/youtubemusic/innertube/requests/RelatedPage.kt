package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.BrowseResponse
import app.vimusic.providers.youtubemusic.innertube.models.Context
import app.vimusic.providers.youtubemusic.innertube.models.MusicCarouselShelfRenderer
import app.vimusic.providers.youtubemusic.innertube.models.NextResponse
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.NextBody
import app.vimusic.providers.youtubemusic.innertube.utils.findSectionByStrapline
import app.vimusic.providers.youtubemusic.innertube.utils.findSectionByTitle
import app.vimusic.providers.youtubemusic.innertube.utils.from
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun YoutubeMusicInnertube.relatedPage(body: NextBody) = runCatchingCancellable {
    val nextResponse = client.post<NextResponse>(
        NEXT, body.copy(context = Context.DefaultWebNoLang),
        fieldMask = "contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)",
    )

    val browseId = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        ?.getOrNull(2)
        ?.tabRenderer
        ?.endpoint
        ?.browseEndpoint
        ?.browseId
        ?: return@runCatchingCancellable null

    val response = client.post<BrowseResponse>(
        BROWSE,
        BrowseBody(browseId = browseId, context = Context.DefaultWebNoLang),
        fieldMask = "contents.sectionListRenderer.contents.musicCarouselShelfRenderer(header.musicCarouselShelfBasicHeaderRenderer(title,strapline),contents($MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK,$MUSIC_TWO_ROW_ITEM_RENDERER_MASK))",
    )

    val sectionListRenderer = response
        .contents
        ?.sectionListRenderer

    YoutubeMusicInnertube.RelatedPage(
        songs = sectionListRenderer
            ?.findSectionByTitle("You might also like")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(YoutubeMusicInnertube.SongItem::from),
        playlists = sectionListRenderer
            ?.findSectionByTitle("Recommended playlists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(YoutubeMusicInnertube.PlaylistItem::from)
            ?.sortedByDescending { it.channel?.name == "YouTube Music" },
        albums = sectionListRenderer
            ?.findSectionByStrapline("MORE FROM")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(YoutubeMusicInnertube.AlbumItem::from),
        artists = sectionListRenderer
            ?.findSectionByTitle("Similar artists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(YoutubeMusicInnertube.ArtistItem::from)
    )
}

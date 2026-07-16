package app.vimusic.providers.newpipe.requests

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.BrowseResponse
import app.vimusic.providers.newpipe.models.Context
import app.vimusic.providers.newpipe.models.MusicCarouselShelfRenderer
import app.vimusic.providers.newpipe.models.NextResponse
import app.vimusic.providers.newpipe.models.bodies.BrowseBody
import app.vimusic.providers.newpipe.models.bodies.NextBody
import app.vimusic.providers.newpipe.utils.findSectionByStrapline
import app.vimusic.providers.newpipe.utils.findSectionByTitle
import app.vimusic.providers.newpipe.utils.from
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun NewPipeMusic.relatedPage(body: NextBody) = runCatchingCancellable {
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

    NewPipeMusic.RelatedPage(
        songs = sectionListRenderer
            ?.findSectionByTitle("You might also like")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(NewPipeMusic.SongItem::from),
        playlists = sectionListRenderer
            ?.findSectionByTitle("Recommended playlists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(NewPipeMusic.PlaylistItem::from)
            ?.sortedByDescending { it.channel?.name == "YouTube Music" },
        albums = sectionListRenderer
            ?.findSectionByStrapline("MORE FROM")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(NewPipeMusic.AlbumItem::from),
        artists = sectionListRenderer
            ?.findSectionByTitle("Similar artists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(NewPipeMusic.ArtistItem::from)
    )
}

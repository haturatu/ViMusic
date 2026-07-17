package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.BrowseResponse
import app.vimusic.providers.youtubemusic.innertube.models.NextResponse
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.NextBody
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun YoutubeMusicInnertube.lyrics(body: NextBody) = runCatchingCancellable {
    val nextResponse = client.post<NextResponse>(
        NEXT, body,
        fieldMask = "contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)",
        context = body.context,
    )

    val tabs = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        .orEmpty()

    // The order of tabs is not stable: for some tracks YouTube inserts an
    // "Up next" or a related-content tab before Lyrics.  Select the actual
    // Lyrics tab first, retaining the old endpoint-based fallback for locales
    // where its title is translated.
    val browseId = (tabs.firstOrNull { tab ->
        tab.tabRenderer?.title.equals("Lyrics", ignoreCase = true)
    } ?: tabs.firstOrNull { tab ->
        tab.tabRenderer?.endpoint?.browseEndpoint?.browseId != null
    })
        ?.tabRenderer
        ?.endpoint
        ?.browseEndpoint
        ?.browseId
        ?: return@runCatchingCancellable null

    val response = client.post<BrowseResponse>(
        BROWSE, BrowseBody(context = body.context, browseId = browseId),
        fieldMask = "contents.sectionListRenderer.contents.musicDescriptionShelfRenderer.description",
        context = body.context,
    )

    response.contents
        ?.sectionListRenderer
        ?.contents
        ?.firstOrNull()
        ?.musicDescriptionShelfRenderer
        ?.description
        ?.text
}

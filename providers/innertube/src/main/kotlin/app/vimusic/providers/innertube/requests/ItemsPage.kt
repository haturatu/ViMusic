package app.vimusic.providers.innertube.requests

import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.BrowseResponse
import app.vimusic.providers.innertube.models.ContinuationResponse
import app.vimusic.providers.innertube.models.GridRenderer
import app.vimusic.providers.innertube.models.MusicResponsiveListItemRenderer
import app.vimusic.providers.innertube.models.MusicShelfRenderer
import app.vimusic.providers.innertube.models.MusicTwoRowItemRenderer
import app.vimusic.providers.innertube.models.bodies.BrowseBody
import app.vimusic.providers.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.utils.runCatchingCancellable
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

suspend fun <T : Innertube.Item> Innertube.itemsPage(
    body: BrowseBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null }
) = runCatchingCancellable {
    val requestBody = body.copy(context = withLatestVisitorData(body.context))

    val response = client.post(BROWSE) {
        setBody(requestBody)
    }.body<BrowseResponse>()
    updateLatestVisitorData(response.responseContext?.visitorData)

    val sectionListRenderer = response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer

    val sectionListRendererContent = sectionListRenderer
        ?.contents
        ?.firstOrNull { it.musicShelfRenderer != null || it.gridRenderer != null }

    itemsPageFromMusicShelRendererOrGridRenderer(
        musicShelfRenderer = sectionListRendererContent
            ?.musicShelfRenderer,
        gridRenderer = sectionListRendererContent
            ?.gridRenderer,
        sectionListContinuation = sectionListRenderer
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation,
        fromMusicResponsiveListItemRenderer = fromMusicResponsiveListItemRenderer,
        fromMusicTwoRowItemRenderer = fromMusicTwoRowItemRenderer
    )
}

suspend fun <T : Innertube.Item> Innertube.itemsPage(
    body: ContinuationBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null }
) = runCatchingCancellable {
    val requestBody = body.copy(context = withLatestVisitorData(body.context))

    val response = client.post(BROWSE) {
        setBody(requestBody)
        parameter("continuation", requestBody.continuation)
        parameter("ctoken", requestBody.continuation)
        parameter("type", "next")
    }.body<ContinuationResponse>()
    updateLatestVisitorData(response.responseContext?.visitorData)

    val sectionListRenderer = response
        .continuationContents
        ?.sectionListContinuation

    val sectionListRendererContent = sectionListRenderer
        ?.contents
        ?.firstOrNull { it.musicShelfRenderer != null || it.gridRenderer != null }

    itemsPageFromMusicShelRendererOrGridRenderer(
        musicShelfRenderer = response
            .continuationContents
            ?.musicShelfContinuation
            ?: sectionListRendererContent?.musicShelfRenderer,
        gridRenderer = response
            .continuationContents
            ?.gridContinuation
            ?: sectionListRendererContent?.gridRenderer,
        sectionListContinuation = sectionListRenderer
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation,
        fromMusicResponsiveListItemRenderer = fromMusicResponsiveListItemRenderer,
        fromMusicTwoRowItemRenderer = fromMusicTwoRowItemRenderer
    )
}

private fun <T : Innertube.Item> itemsPageFromMusicShelRendererOrGridRenderer(
    musicShelfRenderer: MusicShelfRenderer?,
    gridRenderer: GridRenderer?,
    sectionListContinuation: String?,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T?,
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T?
) = when {
    musicShelfRenderer != null -> Innertube.ItemsPage(
        continuation = (
            musicShelfRenderer
            .continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
            ?: sectionListContinuation
            )?.takeIf { it.isNotBlank() },
        items = musicShelfRenderer
            .contents
            ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(fromMusicResponsiveListItemRenderer)
    )

    gridRenderer != null -> Innertube.ItemsPage(
        continuation = (
            gridRenderer
            .continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
            ?: sectionListContinuation
            )?.takeIf { it.isNotBlank() },
        items = gridRenderer
            .items
            ?.mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
            ?.mapNotNull(fromMusicTwoRowItemRenderer)
    )

    else -> null
}

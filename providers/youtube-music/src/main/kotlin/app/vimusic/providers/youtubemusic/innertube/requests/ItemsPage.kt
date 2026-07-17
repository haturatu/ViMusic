package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.BrowseResponse
import app.vimusic.providers.youtubemusic.innertube.models.ContinuationResponse
import app.vimusic.providers.youtubemusic.innertube.models.GridRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicResponsiveListItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicShelfRenderer
import app.vimusic.providers.youtubemusic.innertube.models.MusicTwoRowItemRenderer
import app.vimusic.providers.youtubemusic.innertube.models.bodies.BrowseBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun <T : YoutubeMusicInnertube.Item> YoutubeMusicInnertube.itemsPage(
    body: BrowseBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null }
) = runCatchingCancellable {
    val requestBody = body.copy(context = withLatestVisitorData(body.context))

    val response = client.post<BrowseResponse>(BROWSE, requestBody)
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

suspend fun <T : YoutubeMusicInnertube.Item> YoutubeMusicInnertube.itemsPage(
    body: ContinuationBody,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T? = { null },
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T? = { null }
) = runCatchingCancellable {
    val requestBody = body.copy(context = withLatestVisitorData(body.context))

    val response = client.post<ContinuationResponse>(
        BROWSE,
        requestBody,
        parameters = mapOf(
            "continuation" to requestBody.continuation,
            "ctoken" to requestBody.continuation,
            "type" to "next",
        ),
    )
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

private fun <T : YoutubeMusicInnertube.Item> itemsPageFromMusicShelRendererOrGridRenderer(
    musicShelfRenderer: MusicShelfRenderer?,
    gridRenderer: GridRenderer?,
    sectionListContinuation: String?,
    fromMusicResponsiveListItemRenderer: (MusicResponsiveListItemRenderer) -> T?,
    fromMusicTwoRowItemRenderer: (MusicTwoRowItemRenderer) -> T?
) = when {
    musicShelfRenderer != null -> YoutubeMusicInnertube.ItemsPage(
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

    gridRenderer != null -> YoutubeMusicInnertube.ItemsPage(
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

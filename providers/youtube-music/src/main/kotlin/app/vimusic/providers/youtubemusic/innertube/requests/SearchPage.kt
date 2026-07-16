package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.ContinuationResponse
import app.vimusic.providers.youtubemusic.innertube.models.MusicShelfRenderer
import app.vimusic.providers.youtubemusic.innertube.models.SearchResponse
import app.vimusic.providers.youtubemusic.innertube.models.bodies.ContinuationBody
import app.vimusic.providers.youtubemusic.innertube.models.bodies.SearchBody
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun <T : YoutubeMusicInnertube.Item> YoutubeMusicInnertube.searchPage(
    body: SearchBody,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?
) = runCatchingCancellable {
    val response = client.post<SearchResponse>(
        SEARCH,
        body,
        fieldMask = "contents.tabbedSearchResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents.musicShelfRenderer(continuations,contents.$MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK)",
    )

    response
        .contents
        ?.tabbedSearchResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents
        ?.lastOrNull { it.musicShelfRenderer != null }
        ?.musicShelfRenderer
        ?.toItemsPage(fromMusicShelfRendererContent)
}.also { result ->
    result?.onSuccess { page ->
        logger.info("NewPipe Music search completed: items=${page?.items?.size ?: 0}")
    }?.onFailure { error ->
        logger.warn("NewPipe Music search response could not be parsed", error)
    }
}

suspend fun <T : YoutubeMusicInnertube.Item> YoutubeMusicInnertube.searchPage(
    body: ContinuationBody,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?
) = runCatchingCancellable {
    val response = client.post<ContinuationResponse>(
        SEARCH,
        body,
        fieldMask = "continuationContents.musicShelfContinuation(continuations,contents.$MUSIC_RESPONSIVE_LIST_ITEM_RENDERER_MASK)",
    )

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toItemsPage(fromMusicShelfRendererContent)
}.also { result ->
    result?.onSuccess { page ->
        logger.info("NewPipe Music search continuation completed: items=${page?.items?.size ?: 0}")
    }?.onFailure { error ->
        logger.warn("NewPipe Music search continuation could not be parsed", error)
    }
}

private fun <T : YoutubeMusicInnertube.Item> MusicShelfRenderer?.toItemsPage(
    mapper: (MusicShelfRenderer.Content) -> T?
) = YoutubeMusicInnertube.ItemsPage(
    items = this
        ?.contents
        ?.mapNotNull(mapper),
    continuation = this
        ?.continuations
        ?.firstOrNull()
        ?.nextContinuationData
        ?.continuation
)

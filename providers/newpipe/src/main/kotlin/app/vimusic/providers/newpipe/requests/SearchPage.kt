package app.vimusic.providers.newpipe.requests

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.ContinuationResponse
import app.vimusic.providers.newpipe.models.MusicShelfRenderer
import app.vimusic.providers.newpipe.models.SearchResponse
import app.vimusic.providers.newpipe.models.bodies.ContinuationBody
import app.vimusic.providers.newpipe.models.bodies.SearchBody
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun <T : NewPipeMusic.Item> NewPipeMusic.searchPage(
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

suspend fun <T : NewPipeMusic.Item> NewPipeMusic.searchPage(
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

private fun <T : NewPipeMusic.Item> MusicShelfRenderer?.toItemsPage(
    mapper: (MusicShelfRenderer.Content) -> T?
) = NewPipeMusic.ItemsPage(
    items = this
        ?.contents
        ?.mapNotNull(mapper),
    continuation = this
        ?.continuations
        ?.firstOrNull()
        ?.nextContinuationData
        ?.continuation
)

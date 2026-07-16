package app.vimusic.providers.youtubemusic.innertube.requests

import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.SearchSuggestionsResponse
import app.vimusic.providers.youtubemusic.innertube.models.bodies.SearchSuggestionsBody
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun YoutubeMusicInnertube.searchSuggestions(body: SearchSuggestionsBody) = runCatchingCancellable {
    val response = client.post<SearchSuggestionsResponse>(
        SEARCH_SUGGESTIONS, body,
        fieldMask = "contents.searchSuggestionsSectionRenderer.contents.searchSuggestionRenderer.navigationEndpoint.searchEndpoint.query",
    )

    response
        .contents
        ?.firstOrNull()
        ?.searchSuggestionsSectionRenderer
        ?.contents
        ?.mapNotNull { content ->
            content
                .searchSuggestionRenderer
                ?.navigationEndpoint
                ?.searchEndpoint
                ?.query
        }
}

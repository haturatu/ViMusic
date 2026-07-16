package app.vimusic.providers.newpipe.requests

import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.models.SearchSuggestionsResponse
import app.vimusic.providers.newpipe.models.bodies.SearchSuggestionsBody
import app.vimusic.providers.utils.runCatchingCancellable

suspend fun NewPipeMusic.searchSuggestions(body: SearchSuggestionsBody) = runCatchingCancellable {
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

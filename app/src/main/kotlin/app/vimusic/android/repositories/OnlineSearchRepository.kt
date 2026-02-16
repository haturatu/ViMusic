package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.SearchQuery
import app.vimusic.android.query
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.SearchSuggestionsBody
import app.vimusic.providers.innertube.requests.searchSuggestions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

interface OnlineSearchRepository {
    fun observeHistory(input: String): Flow<List<SearchQuery>>
    suspend fun fetchSuggestions(input: String): Result<List<String>?>?
    fun deleteHistory(searchQuery: SearchQuery)
    fun saveHistory(text: String)
}

object DatabaseOnlineSearchRepository : OnlineSearchRepository {
    override fun observeHistory(input: String): Flow<List<SearchQuery>> =
        Database.queries("%$input%")
            .distinctUntilChanged { old, new -> old.size == new.size }

    override suspend fun fetchSuggestions(input: String): Result<List<String>?>? =
        Innertube.searchSuggestions(body = SearchSuggestionsBody(input = input))

    override fun deleteHistory(searchQuery: SearchQuery) {
        query { Database.delete(searchQuery) }
    }

    override fun saveHistory(text: String) {
        query {
            Database.insert(SearchQuery(query = text))
        }
    }
}

package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.SearchQuery
import app.vimusic.android.query
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.SearchSuggestionsBody
import app.vimusic.providers.youtubemusic.innertube.requests.searchSuggestions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

interface OnlineSearchRepository {
    fun observeHistory(input: String): Flow<List<SearchQuery>>
    fun observeHistoryCount(): Flow<Int>
    suspend fun fetchSuggestions(input: String): Result<List<String>?>?
    suspend fun deleteHistory(searchQuery: SearchQuery)
    suspend fun clearHistory()
    suspend fun saveHistory(text: String)
}

object DatabaseOnlineSearchRepository : OnlineSearchRepository {
    override fun observeHistory(input: String): Flow<List<SearchQuery>> =
        Database.queries("%$input%")
            .distinctUntilChanged { old, new -> old.size == new.size }

    override fun observeHistoryCount(): Flow<Int> = Database.queriesCount().distinctUntilChanged()

    override suspend fun fetchSuggestions(input: String): Result<List<String>?>? =
        YoutubeMusicInnertube.searchSuggestions(body = SearchSuggestionsBody(input = input))

    override suspend fun deleteHistory(searchQuery: SearchQuery) {
        query { Database.delete(searchQuery) }
    }

    override suspend fun clearHistory() {
        query(Database::clearQueries)
    }

    override suspend fun saveHistory(text: String) {
        query {
            Database.insert(SearchQuery(query = text))
        }
    }
}

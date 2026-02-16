package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.query
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.NextBody
import app.vimusic.providers.innertube.requests.relatedPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HomeQuickPicksRepository {
    fun observeTrendingSong(): Flow<Song?>
    fun observeLastInteractionSong(): Flow<Song?>
    suspend fun fetchRelatedPage(videoId: String): Result<Innertube.RelatedPage?>?
    fun clearEventsFor(songId: String)
    fun getCachedQuickPicksIfAvailable(): Innertube.RelatedPage?
    fun cacheQuickPicks(page: Innertube.RelatedPage)
    fun clearCachedQuickPicks()
}

object DatabaseHomeQuickPicksRepository : HomeQuickPicksRepository {
    override fun observeTrendingSong(): Flow<Song?> =
        Database.trending().map { songs -> songs.firstOrNull() }

    override fun observeLastInteractionSong(): Flow<Song?> =
        Database.events().map { events -> events.firstOrNull()?.song }

    override suspend fun fetchRelatedPage(videoId: String): Result<Innertube.RelatedPage?>? =
        Innertube.relatedPage(body = NextBody(videoId = videoId))

    override fun clearEventsFor(songId: String) {
        query { Database.clearEventsFor(songId) }
    }

    override fun getCachedQuickPicksIfAvailable(): Innertube.RelatedPage? {
        if (!DataPreferences.shouldCacheQuickPicks) return null
        val cached = DataPreferences.cachedQuickPicks
        return if (
            cached.albums.isNullOrEmpty() &&
            cached.artists.isNullOrEmpty() &&
            cached.playlists.isNullOrEmpty() &&
            cached.songs.isNullOrEmpty()
        ) null else cached
    }

    override fun cacheQuickPicks(page: Innertube.RelatedPage) {
        DataPreferences.cachedQuickPicks = page
    }

    override fun clearCachedQuickPicks() {
        DataPreferences.cachedQuickPicks = Innertube.RelatedPage()
    }
}

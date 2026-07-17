package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.query
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.NextBody
import app.vimusic.providers.youtubemusic.innertube.requests.relatedPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HomeQuickPicksRepository {
    fun observeTrendingSong(): Flow<Song?>
    fun observeLastInteractionSong(): Flow<Song?>
    suspend fun fetchRelatedPage(videoId: String): Result<YoutubeMusicInnertube.RelatedPage?>?
    fun clearEventsFor(songId: String)
    fun getCachedQuickPicksIfAvailable(): YoutubeMusicInnertube.RelatedPage?
    fun cacheQuickPicks(page: YoutubeMusicInnertube.RelatedPage)
    fun clearCachedQuickPicks()
}

object DatabaseHomeQuickPicksRepository : HomeQuickPicksRepository {
    override fun observeTrendingSong(): Flow<Song?> =
        Database.trending().map { songs -> songs.firstOrNull() }

    override fun observeLastInteractionSong(): Flow<Song?> =
        Database.events().map { events -> events.firstOrNull()?.song }

    override suspend fun fetchRelatedPage(videoId: String): Result<YoutubeMusicInnertube.RelatedPage?>? =
        YoutubeMusicInnertube.relatedPage(body = NextBody(videoId = videoId))

    override fun clearEventsFor(songId: String) {
        query { Database.clearEventsFor(songId) }
    }

    override fun getCachedQuickPicksIfAvailable(): YoutubeMusicInnertube.RelatedPage? {
        if (!DataPreferences.shouldCacheQuickPicks) return null
        val cached = DataPreferences.cachedQuickPicks
        return if (
            cached.albums.isNullOrEmpty() &&
            cached.artists.isNullOrEmpty() &&
            cached.playlists.isNullOrEmpty() &&
            cached.songs.isNullOrEmpty()
        ) null else cached
    }

    override fun cacheQuickPicks(page: YoutubeMusicInnertube.RelatedPage) {
        if (DataPreferences.shouldCacheQuickPicks) DataPreferences.cachedQuickPicks = page
    }

    override fun clearCachedQuickPicks() {
        DataPreferences.cachedQuickPicks = YoutubeMusicInnertube.RelatedPage()
    }
}

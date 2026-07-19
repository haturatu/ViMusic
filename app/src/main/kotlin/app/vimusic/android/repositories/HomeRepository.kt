package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.models.PipedSession
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.PlaylistPreview
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.query
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.core.data.enums.SortOrder
import app.vimusic.providers.piped.Piped
import app.vimusic.providers.piped.models.PlaylistPreview as PipedPlaylistPreview
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.NextBody
import app.vimusic.providers.youtubemusic.innertube.requests.discoverPage
import app.vimusic.providers.youtubemusic.innertube.requests.relatedPage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface HomeRepository {
    suspend fun fetchDiscoverPage(): Result<YoutubeMusicInnertube.DiscoverPage>?
    fun observePlaylistPreviews(sortBy: PlaylistSortBy, sortOrder: SortOrder): Flow<List<PlaylistPreview>>
    fun observePipedPlaylists(): Flow<Map<PipedSession, List<PipedPlaylistPreview>?>>
    fun createPlaylist(name: String)
    fun observeTrendingSong(): Flow<Song?>
    fun observeLastInteractionSong(): Flow<Song?>
    suspend fun fetchRelatedPage(videoId: String): Result<YoutubeMusicInnertube.RelatedPage?>?
    fun clearEventsFor(songId: String)
    fun getCachedQuickPicksIfAvailable(): YoutubeMusicInnertube.RelatedPage?
    fun cacheQuickPicks(page: YoutubeMusicInnertube.RelatedPage)
    fun clearCachedQuickPicks()
}

object DefaultHomeRepository : HomeRepository {
    override suspend fun fetchDiscoverPage(): Result<YoutubeMusicInnertube.DiscoverPage>? =
        YoutubeMusicInnertube.discoverPage()

    override fun observePlaylistPreviews(
        sortBy: PlaylistSortBy,
        sortOrder: SortOrder
    ): Flow<List<PlaylistPreview>> = Database.playlistPreviews(sortBy = sortBy, sortOrder = sortOrder)

    override fun observePipedPlaylists(): Flow<Map<PipedSession, List<PipedPlaylistPreview>?>> = flow {
        Database.pipedSessions().collect { sessions ->
            emit(coroutineScope {
                sessions.associateWith { session ->
                    async { Piped.playlist.list(session = session.toApiSession())?.getOrNull() }
                }.mapValues { (_, deferred) -> deferred.await() }
            })
        }
    }

    override fun createPlaylist(name: String) {
        query { Database.insert(Playlist(name = name)) }
    }

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
        return cached.takeUnless {
            it.albums.isNullOrEmpty() &&
                it.artists.isNullOrEmpty() &&
                it.playlists.isNullOrEmpty() &&
                it.songs.isNullOrEmpty()
        }
    }

    override fun cacheQuickPicks(page: YoutubeMusicInnertube.RelatedPage) {
        if (DataPreferences.shouldCacheQuickPicks) DataPreferences.cachedQuickPicks = page
    }

    override fun clearCachedQuickPicks() {
        DataPreferences.cachedQuickPicks = YoutubeMusicInnertube.RelatedPage()
    }
}

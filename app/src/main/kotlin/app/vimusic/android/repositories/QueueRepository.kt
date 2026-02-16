package app.vimusic.android.repositories

import androidx.media3.common.MediaItem
import app.vimusic.android.Database
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.PlaylistPreview
import app.vimusic.android.models.SongPlaylistMap
import app.vimusic.android.transaction
import app.vimusic.android.utils.asMediaItem
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.core.data.enums.SortOrder
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.NextBody
import app.vimusic.providers.innertube.requests.nextPage
import kotlinx.coroutines.flow.Flow

interface QueueRepository {
    suspend fun fetchSuggestions(videoId: String): List<MediaItem>?
    fun observePlaylistPreviews(sortBy: PlaylistSortBy, sortOrder: SortOrder): Flow<List<PlaylistPreview>>
    fun addQueueToPlaylist(playlist: Playlist, index: Int, mediaItems: List<MediaItem>)
}

object DatabaseQueueRepository : QueueRepository {
    override suspend fun fetchSuggestions(videoId: String): List<MediaItem>? =
        Innertube.nextPage(NextBody(videoId = videoId))
            ?.getOrNull()
            ?.itemsPage
            ?.items
            ?.map { it.asMediaItem }

    override fun observePlaylistPreviews(
        sortBy: PlaylistSortBy,
        sortOrder: SortOrder
    ): Flow<List<PlaylistPreview>> = Database.playlistPreviews(sortBy = sortBy, sortOrder = sortOrder)

    override fun addQueueToPlaylist(playlist: Playlist, index: Int, mediaItems: List<MediaItem>) {
        transaction {
            val playlistId = Database.insert(playlist).takeIf { it != -1L } ?: playlist.id

            mediaItems.forEachIndexed { i, mediaItem ->
                Database.insert(mediaItem)
                Database.insert(
                    SongPlaylistMap(
                        songId = mediaItem.mediaId,
                        playlistId = playlistId,
                        position = index + i
                    )
                )
            }
        }
    }
}

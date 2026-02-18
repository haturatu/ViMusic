package app.vimusic.android.repositories

import android.database.sqlite.SQLiteConstraintException
import app.vimusic.android.Database
import app.vimusic.android.models.Event
import app.vimusic.android.models.Format
import app.vimusic.android.models.QueuedMediaItem
import app.vimusic.android.query
import app.vimusic.android.transaction
import androidx.media3.common.MediaItem
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.PlayerBody
import app.vimusic.providers.innertube.requests.player
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    fun insertSong(mediaItem: MediaItem)
    fun incrementTotalPlayTimeMs(songId: String, totalPlayTimeMs: Long)
    fun insertEvent(event: Event)
    fun saveQueue(queue: List<QueuedMediaItem>)
    fun loadQueue(): List<QueuedMediaItem>
    fun clearQueue()
    suspend fun filterBlacklistedSongs(items: List<MediaItem>): List<MediaItem>
    fun isFavoriteNow(songId: String): Boolean
    fun updateDurationText(songId: String, durationText: String)
    fun insertFormat(format: Format)
    fun observeLikedAt(songId: String): Flow<Long?>
    fun setLikedAt(songId: String, likedAt: Long?)
    fun observeLoudnessDb(songId: String): Flow<Float?>
    fun observeFormat(songId: String): Flow<Format?>
    suspend fun refreshFormat(songId: String, mediaItem: MediaItem?)
    fun observeLoudnessBoost(songId: String): Flow<Float?>
    fun setLoudnessBoost(songId: String, loudnessBoost: Float?)
}

object DatabasePlayerRepository : PlayerRepository {
    override fun insertSong(mediaItem: MediaItem) {
        Database.insert(mediaItem)
    }

    override fun incrementTotalPlayTimeMs(songId: String, totalPlayTimeMs: Long) {
        query { Database.incrementTotalPlayTimeMs(songId, totalPlayTimeMs) }
    }

    override fun insertEvent(event: Event) {
        query {
            runCatching { Database.insert(event) }
                .onFailure { throwable ->
                    if (throwable !is SQLiteConstraintException) throw throwable
                }
        }
    }

    override fun saveQueue(queue: List<QueuedMediaItem>) {
        transaction {
            Database.clearQueue()
            Database.insert(queue)
        }.join()
    }

    override fun loadQueue(): List<QueuedMediaItem> = Database.queue()

    override fun clearQueue() {
        transaction { Database.clearQueue() }
    }

    override suspend fun filterBlacklistedSongs(items: List<MediaItem>): List<MediaItem> =
        Database.filterBlacklistedSongs(items)

    override fun isFavoriteNow(songId: String): Boolean =
        runCatching { Database.likedAtNow(songId) != null }.getOrDefault(false)

    override fun updateDurationText(songId: String, durationText: String) {
        Database.updateDurationText(songId, durationText)
    }

    override fun insertFormat(format: Format) {
        transaction { Database.insert(format) }
    }

    override fun observeLikedAt(songId: String): Flow<Long?> = Database.likedAt(songId)

    override fun setLikedAt(songId: String, likedAt: Long?) {
        query { Database.like(songId, likedAt) }
    }

    override fun observeLoudnessDb(songId: String): Flow<Float?> = Database.loudnessDb(songId)

    override fun observeFormat(songId: String): Flow<Format?> = Database.format(songId)

    override suspend fun refreshFormat(songId: String, mediaItem: MediaItem?) {
        mediaItem ?: return

        Innertube.player(PlayerBody(videoId = songId))
            ?.onSuccess { response ->
                response?.streamingData?.highestQualityFormat?.let { format ->
                    Database.insert(mediaItem)
                    Database.insert(
                        Format(
                            songId = songId,
                            itag = format.itag,
                            mimeType = format.mimeType,
                            bitrate = format.bitrate,
                            loudnessDb = response.playerConfig?.audioConfig?.normalizedLoudnessDb,
                            contentLength = format.contentLength,
                            lastModified = format.lastModified
                        )
                    )
                }
            }
    }

    override fun observeLoudnessBoost(songId: String): Flow<Float?> = Database.loudnessBoost(songId)

    override fun setLoudnessBoost(songId: String, loudnessBoost: Float?) {
        transaction { Database.setLoudnessBoost(songId = songId, loudnessBoost = loudnessBoost) }
    }
}

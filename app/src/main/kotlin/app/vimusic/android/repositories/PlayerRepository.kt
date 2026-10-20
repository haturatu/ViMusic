package app.vimusic.android.repositories

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.media3.common.MediaItem
import app.vimusic.android.Database
import app.vimusic.android.internal
import app.vimusic.android.models.Event
import app.vimusic.android.models.Format
import app.vimusic.android.models.QueuedMediaItem
import app.vimusic.android.query
import app.vimusic.android.transaction
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.models.bodies.PlayerBody
import app.vimusic.providers.youtubemusic.innertube.requests.player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface PlayerRepository {
    suspend fun insertSong(mediaItem: MediaItem)
    suspend fun recordPlayback(mediaItem: MediaItem, totalPlayTimeMs: Long, event: Event?)
    fun saveQueue(queue: List<QueuedMediaItem>)
    suspend fun loadQueue(): List<QueuedMediaItem>
    suspend fun filterBlacklistedSongs(items: List<MediaItem>): List<MediaItem>
    fun isFavoriteNow(songId: String): Boolean
    suspend fun saveStreamMetadata(mediaItem: MediaItem?, format: Format, durationText: String?)
    fun observeLikedAt(songId: String): Flow<Long?>
    suspend fun setLikedAt(songId: String, likedAt: Long?)
    fun observeLoudnessDb(songId: String): Flow<Float?>
    fun observeFormat(songId: String): Flow<Format?>
    suspend fun refreshFormat(songId: String, mediaItem: MediaItem?)
    fun observeLoudnessBoost(songId: String): Flow<Float?>
    suspend fun setLoudnessBoost(songId: String, loudnessBoost: Float?)
}

object DatabasePlayerRepository : PlayerRepository {
    private const val TAG = "DatabasePlayerRepository"
    private const val MAX_EVENT_HISTORY_COUNT = 500
    private const val EVENT_PRUNE_BATCH_SIZE = 350

    // Queue writes outlive the service and run in order, including its final snapshot.
    private val queueDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val queueScope = CoroutineScope(SupervisorJob() + queueDispatcher)
    private var savedQueue: List<QueuedMediaItem>? = null

    override suspend fun insertSong(mediaItem: MediaItem) {
        Database.insert(mediaItem)
    }

    override suspend fun recordPlayback(mediaItem: MediaItem, totalPlayTimeMs: Long, event: Event?) {
        transaction {
            Database.insert(mediaItem)
            if (totalPlayTimeMs > 0) {
                Database.incrementTotalPlayTimeMs(mediaItem.mediaId, totalPlayTimeMs)
            }
            if (event != null) {
                Database.insert(event)
                if (Database.eventsCountNow() > MAX_EVENT_HISTORY_COUNT) {
                    Database.deleteOldestEvents(EVENT_PRUNE_BATCH_SIZE)
                }
            }
        }
    }

    override fun saveQueue(queue: List<QueuedMediaItem>) {
        val currentItem = queue.firstOrNull { it.position != null } ?: return
        val currentPosition = currentItem.position ?: return
        queueScope.launch {
            runCatching {
                Database.internal.runInTransaction {
                    val previous = savedQueue
                    val sameItems = previous != null && previous.size == queue.size &&
                        queue.indices.all { index ->
                            previous[index].id == queue[index].id &&
                                previous[index].mediaItem === queue[index].mediaItem
                        }
                    val updated = if (sameItems) {
                        Database.updateQueuePosition(currentItem.id, currentPosition)
                    } else 0

                    if (!sameItems || updated != queue.size) {
                        Database.clearQueue()
                        Database.insert(queue)
                    }
                }
                savedQueue = queue
            }.onFailure {
                savedQueue = null
            }
        }
    }

    override suspend fun loadQueue(): List<QueuedMediaItem> =
        withContext(queueDispatcher) { Database.queue() }

    override suspend fun filterBlacklistedSongs(items: List<MediaItem>): List<MediaItem> =
        Database.filterBlacklistedSongs(items)

    override fun isFavoriteNow(songId: String): Boolean =
        runCatching { Database.likedAtNow(songId) != null }.getOrDefault(false)

    override suspend fun saveStreamMetadata(mediaItem: MediaItem?, format: Format, durationText: String?) {
        transaction {
            mediaItem?.let { Database.insert(it) }
            insertFormatMetadataIgnoringMissingSong(format)
            durationText?.let { Database.updateDurationText(format.songId, it) }
        }
    }

    override fun observeLikedAt(songId: String): Flow<Long?> = Database.likedAt(songId)

    override suspend fun setLikedAt(songId: String, likedAt: Long?) {
        query { Database.like(songId, likedAt) }
    }

    override fun observeLoudnessDb(songId: String): Flow<Float?> = Database.loudnessDb(songId)

    override fun observeFormat(songId: String): Flow<Format?> = Database.format(songId)

    override suspend fun refreshFormat(songId: String, mediaItem: MediaItem?) {
        mediaItem ?: return

        YoutubeMusicInnertube.player(PlayerBody(videoId = songId))
            ?.onSuccess { response ->
                response?.streamingData?.highestQualityFormat?.let { format ->
                    transaction {
                        Database.insert(mediaItem)
                        insertFormatIgnoringMissingSong(
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
    }

    override fun observeLoudnessBoost(songId: String): Flow<Float?> = Database.loudnessBoost(songId)

    override suspend fun setLoudnessBoost(songId: String, loudnessBoost: Float?) {
        transaction { Database.setLoudnessBoost(songId = songId, loudnessBoost = loudnessBoost) }
    }

    private fun insertFormatIgnoringMissingSong(format: Format) {
        runCatching {
            Database.insert(format)
        }.onFailure { throwable ->
            if (throwable is SQLiteConstraintException) {
                Log.w(TAG, "Skipping format insert without song ${format.songId}", throwable)
            } else {
                throw throwable
            }
        }
    }

    private fun insertFormatMetadataIgnoringMissingSong(format: Format) {
        runCatching {
            if (Database.updateFormatMetadata(format.songId, format.itag, format.mimeType, format.bitrate) == 0) {
                Database.insertFormatMetadata(format)
            }
        }.onFailure { throwable ->
            if (throwable is SQLiteConstraintException) {
                Log.w(TAG, "Skipping format metadata insert without song ${format.songId}", throwable)
            } else {
                throw throwable
            }
        }
    }
}

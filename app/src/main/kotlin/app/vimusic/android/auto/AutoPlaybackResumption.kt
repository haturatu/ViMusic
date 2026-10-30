package app.vimusic.android.auto

import android.content.ContentUris
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import app.vimusic.android.models.QueuedMediaItem
import app.vimusic.android.preferences.PlayerPreferences
import app.vimusic.android.repositories.PlayerRepository
import app.vimusic.android.service.LOCAL_KEY_PREFIX
import app.vimusic.core.ui.utils.songBundle

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoPlaybackResumption(private val repository: PlayerRepository) {
    private val requestGeneration = java.util.concurrent.atomic.AtomicLong(0)

    fun markExplicitRequest() { requestGeneration.incrementAndGet() }
    fun generation(): Long = requestGeneration.get()
    fun canRestore(generation: Long, player: Player) =
        generation == requestGeneration.get() && player.mediaItemCount == 0

    suspend fun load(): List<QueuedMediaItem> =
        if (PlayerPreferences.persistentQueue) repository.loadQueue() else emptyList()

    fun resolve(queue: List<QueuedMediaItem>): MediaSession.MediaItemsWithStartPosition {
        check(queue.isNotEmpty()) { "No saved playback queue" }
        val index = queue.indexOfFirst { it.position != null }.coerceAtLeast(0)
        return MediaSession.MediaItemsWithStartPosition(
            queue.map { saved ->
                val item = restoreItem(saved.mediaItem)
                val extras = android.os.Bundle(item.mediaMetadata.extras ?: android.os.Bundle()).apply {
                    songBundle.isFromPersistentQueue = true
                }
                item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
            }, index, queue[index].position?.coerceAtLeast(0) ?: 0,
        )
    }

    fun restoreModes(queue: List<QueuedMediaItem>, player: Player) {
        val current = queue.firstOrNull { it.position != null } ?: queue.firstOrNull() ?: return
        player.repeatMode = current.repeatMode.coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
        player.shuffleModeEnabled = current.shuffleModeEnabled
    }

    companion object {
        fun restoreItem(item: MediaItem): MediaItem {
            val uri = if (item.mediaId.startsWith(LOCAL_KEY_PREFIX)) {
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    item.mediaId.removePrefix(LOCAL_KEY_PREFIX).toLong(),
                )
            } else item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: android.net.Uri.parse(item.mediaId)
            return item.buildUpon().setUri(uri).setCustomCacheKey(item.mediaId).build()
        }
    }
}

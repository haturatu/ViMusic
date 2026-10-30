package app.vimusic.android.auto

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.vimusic.android.utils.enqueue

/** All callers operate on the service player, on its application thread. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoQueueController(val player: Player) {
    fun append(items: List<MediaItem>) {
        if (items.isNotEmpty()) player.enqueue(items)
    }

    fun playNext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (player.mediaItemCount == 0) player.addMediaItems(items)
        else player.addMediaItems(player.currentMediaItemIndex + 1, items)
    }

    fun remove(index: Int, expectedId: String?) {
        require(index in 0 until player.mediaItemCount)
        require(expectedId == null || player.getMediaItemAt(index).mediaId == expectedId)
        player.removeMediaItem(index)
    }

    fun move(from: Int, to: Int, expectedId: String?) {
        require(from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount)
        require(expectedId == null || player.getMediaItemAt(from).mediaId == expectedId)
        player.moveMediaItem(from, to)
    }

    companion object {
        fun signature(player: Player): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            for (index in 0 until player.mediaItemCount) {
                digest.update(player.getMediaItemAt(index).mediaId.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    fun snapshot(): List<MediaItem> = (0 until player.mediaItemCount).map(player::getMediaItemAt)
}

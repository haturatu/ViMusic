package app.vimusic.android.repositories

import androidx.media3.common.MediaItem
import app.vimusic.android.Database
import app.vimusic.android.transaction

interface PrecacheRepository {
    fun isFavorite(mediaId: String): Boolean
    fun insertMediaItem(mediaItem: MediaItem): Result<Unit>
}

object DatabasePrecacheRepository : PrecacheRepository {
    override fun isFavorite(mediaId: String): Boolean =
        runCatching { Database.likedAtNow(mediaId) != null }.getOrDefault(false)

    override fun insertMediaItem(mediaItem: MediaItem): Result<Unit> = runCatching {
        transaction { Database.insert(mediaItem) }
    }.map { Unit }
}

package app.vimusic.android.repositories

import androidx.media3.common.MediaItem
import app.vimusic.android.Database
import app.vimusic.android.query
import app.vimusic.android.transaction
import app.vimusic.android.utils.runSuspendCatching

interface PrecacheRepository {
    suspend fun isFavorite(mediaId: String): Boolean
    suspend fun insertMediaItem(mediaItem: MediaItem): Result<Unit>
}

object DatabasePrecacheRepository : PrecacheRepository {
    override suspend fun isFavorite(mediaId: String): Boolean =
        runSuspendCatching { query { Database.likedAtNow(mediaId) != null } }.getOrDefault(false)

    override suspend fun insertMediaItem(mediaItem: MediaItem): Result<Unit> = runSuspendCatching {
        transaction { Database.insert(mediaItem) }
    }
}

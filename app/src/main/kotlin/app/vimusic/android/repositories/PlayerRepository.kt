package app.vimusic.android.repositories

import app.vimusic.android.Database
import app.vimusic.android.query
import app.vimusic.android.transaction
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    fun observeLikedAt(songId: String): Flow<Long?>
    fun setLikedAt(songId: String, likedAt: Long?)
    fun observeLoudnessBoost(songId: String): Flow<Float?>
    fun setLoudnessBoost(songId: String, loudnessBoost: Float?)
}

object DatabasePlayerRepository : PlayerRepository {
    override fun observeLikedAt(songId: String): Flow<Long?> = Database.likedAt(songId)

    override fun setLikedAt(songId: String, likedAt: Long?) {
        query { Database.like(songId, likedAt) }
    }

    override fun observeLoudnessBoost(songId: String): Flow<Float?> = Database.loudnessBoost(songId)

    override fun setLoudnessBoost(songId: String, loudnessBoost: Float?) {
        transaction { Database.setLoudnessBoost(songId = songId, loudnessBoost = loudnessBoost) }
    }
}

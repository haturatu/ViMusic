package app.vimusic.android.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Immutable
@Entity
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artistsText: String? = null,
    val durationText: String?,
    val thumbnailUrl: String?,
    val likedAt: Long? = null,
    val totalPlayTimeMs: Long = 0,
    val loudnessBoost: Float? = null,
    @ColumnInfo(defaultValue = "false")
    val blacklisted: Boolean = false,
    @ColumnInfo(defaultValue = "false")
    val explicit: Boolean = false
) {
    fun toggleLike() = copy(likedAt = if (likedAt == null) System.currentTimeMillis() else null)
}

/**
 * External-content FTS index for library search. Keeping the song payload in Song avoids
 * duplicating it in the index while allowing prefix searches to be answered by SQLite.
 */
@Fts4(contentEntity = Song::class)
@Entity(tableName = "SongFts")
data class SongFts(
    val title: String,
    val artistsText: String?
)

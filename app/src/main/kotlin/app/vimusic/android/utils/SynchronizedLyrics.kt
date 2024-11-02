package app.vimusic.android.utils

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.parcelize.Parcelize

@Stable
class SynchronizedLyrics(
    val sentences: ImmutableMap<Long, String>,
    private val positionProvider: () -> Long
) {
    var index by mutableIntStateOf(currentIndex)
        private set

    private val currentIndex: Int
        get() {
            var index = -1
            for ((key) in sentences) {
                if (key >= positionProvider()) break
                index++
            }
            return if (index == -1) 0 else index
        }

    fun update(): Boolean {
        val newIndex = currentIndex
        return if (newIndex != index) {
            index = newIndex
            true
        } else false
    }
}

@Parcelize
@Immutable
data class SynchronizedLyricsState(
    val sentences: Map<Long, String>?,
    val offset: Long
) : Parcelable

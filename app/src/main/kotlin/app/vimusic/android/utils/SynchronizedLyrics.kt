package app.vimusic.android.utils

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.parcelize.Parcelize

@Stable
class SynchronizedLyrics(
    sentences: Map<Long, String>,
    private val positionProvider: () -> Long
) {
    private val timestamps = sentences.keys.sorted().toLongArray()
    val sentences: ImmutableList<String> = timestamps.map(sentences::getValue).toImmutableList()

    var index by mutableIntStateOf(currentIndex)
        private set

    private val currentIndex: Int
        get() = indexAt(positionProvider())

    private fun indexAt(position: Long): Int {
        val result = timestamps.binarySearch(position)
        return if (result >= 0) result else (-result - 2).coerceAtLeast(0)
    }

    fun update(): Boolean {
        val newIndex = currentIndex
        return if (newIndex != index) {
            index = newIndex
            true
        } else false
    }

    /** Returns the delay until the following lyric line, or null after the final line. */
    fun delayUntilNextLine(): Long? {
        val position = positionProvider()
        val nextIndex = indexAt(position) + 1
        return timestamps.getOrNull(nextIndex)
            ?.minus(position)
            ?.coerceAtLeast(1L)
    }
}

@Parcelize
@Immutable
data class SynchronizedLyricsState(
    val sentences: Map<Long, String>?,
    val offset: Long
) : Parcelable

package app.vimusic.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Lightweight UI state holder to unify list rendering decisions.
 */
data class SongListState<T>(
    val items: List<T>,
    val isLoading: Boolean
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

@Composable
fun <T> rememberSongListState(
    items: List<T>?,
    isLoading: Boolean
): SongListState<T> = remember(items, isLoading) {
    SongListState(items = items ?: emptyList(), isLoading = isLoading)
}

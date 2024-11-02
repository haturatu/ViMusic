package app.vimusic.android.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

data class ScrollingInfo(
    val isScrollingDown: Boolean = false,
    val isFar: Boolean = false,
    val isReversed: Boolean = false
)

@Composable
fun LazyListState.scrollingInfo(key: Any = Unit): ScrollingInfo {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }

    return remember(this, key) {
        derivedStateOf {
            val isScrollingDown =
                if (previousIndex == firstVisibleItemIndex) firstVisibleItemScrollOffset > previousScrollOffset
                else firstVisibleItemIndex > previousIndex
            val isFar = firstVisibleItemIndex > layoutInfo.visibleItemsInfo.size

            previousIndex = firstVisibleItemIndex
            previousScrollOffset = firstVisibleItemScrollOffset

            ScrollingInfo(
                isScrollingDown = isScrollingDown,
                isFar = isFar,
                isReversed = layoutInfo.reverseLayout
            )
        }
    }.value
}

@Composable
fun LazyGridState.scrollingInfo(key: Any = Unit): ScrollingInfo {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }

    return remember(this, key) {
        derivedStateOf {
            val isScrollingDown =
                if (previousIndex == firstVisibleItemIndex) firstVisibleItemScrollOffset > previousScrollOffset
                else firstVisibleItemIndex > previousIndex
            val isFar = firstVisibleItemIndex > layoutInfo.visibleItemsInfo.size

            previousIndex = firstVisibleItemIndex
            previousScrollOffset = firstVisibleItemScrollOffset

            ScrollingInfo(
                isScrollingDown = isScrollingDown,
                isFar = isFar,
                isReversed = layoutInfo.reverseLayout
            )
        }
    }.value
}

@Composable
fun ScrollState.scrollingInfo(key: Any = Unit): ScrollingInfo {
    var previousValue by remember(this) { mutableIntStateOf(value) }

    return remember(this, key) {
        derivedStateOf {
            val isScrollingDown = value > previousValue
            previousValue = value

            ScrollingInfo(
                isScrollingDown = isScrollingDown,
                isFar = false,
                isReversed = false
            )
        }
    }.value
}

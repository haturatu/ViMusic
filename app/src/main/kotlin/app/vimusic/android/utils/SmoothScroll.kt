package app.vimusic.android.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

suspend fun LazyGridState.smoothScrollToTop() {
    if (firstVisibleItemIndex > layoutInfo.visibleItemsInfo.size)
        scrollToItem(layoutInfo.visibleItemsInfo.size)
    animateScrollToItem(0)
}

suspend fun LazyListState.smoothScrollToTop() {
    if (firstVisibleItemIndex > layoutInfo.visibleItemsInfo.size)
        scrollToItem(layoutInfo.visibleItemsInfo.size)
    animateScrollToItem(0)
}

suspend fun ScrollState.smoothScrollToTop() = animateScrollTo(0)
suspend fun ScrollState.smoothScrollToBottom() = animateScrollTo(maxValue)

package app.vimusic.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.vimusic.android.ui.components.ShimmerHost
import app.vimusic.android.ui.items.SongItemPlaceholder
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.R
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance

@Composable
fun SongCollectionScreen(
    thumbnailContent: @Composable () -> Unit,
    headerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
        .asPaddingValues(),
    listBackground: Color = LocalAppearance.current.colorPalette.background0,
    shuffleIcon: Int = R.drawable.shuffle,
    shuffleVisible: Boolean = true,
    onShuffle: (() -> Unit)? = null,
    floatingActionsContent: (@Composable RowScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) = LayoutWithAdaptiveThumbnail(
    thumbnailContent = thumbnailContent,
    modifier = modifier
) {
    Box {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .background(listBackground)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                headerContent()
            }

            content()
        }

        if (shuffleVisible && (onShuffle != null || floatingActionsContent != null)) {
            FloatingActionsContainerWithScrollToTop(
                lazyListState = listState,
                icon = shuffleIcon,
                onClick = onShuffle,
                actionsContent = floatingActionsContent
            )
        }
    }
}

fun <T> LazyListScope.songCollectionItems(
    items: List<T>,
    isLoading: Boolean,
    key: ((index: Int, item: T) -> Any)? = null,
    contentType: ((index: Int, item: T) -> Any?)? = null,
    itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) {
    if (!isLoading) {
        itemsIndexed(
            items = items,
            key = key,
            contentType = contentType ?: { _, _ -> null },
            itemContent = itemContent,
        )
    } else {
        item(key = "loading") {
            ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }
            }
        }
    }
}

fun matchesSongCollectionQuery(
    query: String?,
    vararg values: String?
): Boolean {
    val normalizedQuery = query?.trim().orEmpty()

    return normalizedQuery.isEmpty() || values.any {
        it?.contains(normalizedQuery, ignoreCase = true) == true
    }
}

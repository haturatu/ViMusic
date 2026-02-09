package app.vimusic.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.R
import app.vimusic.core.ui.LocalAppearance

@Composable
fun SongListScaffold(
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

        if (shuffleVisible && onShuffle != null) {
            FloatingActionsContainerWithScrollToTop(
                lazyListState = listState,
                icon = shuffleIcon,
                onClick = onShuffle
            )
        }
    }
}

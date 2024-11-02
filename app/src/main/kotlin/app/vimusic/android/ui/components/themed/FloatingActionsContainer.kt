package app.vimusic.android.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.R
import app.vimusic.android.utils.ScrollingInfo
import app.vimusic.android.utils.scrollingInfo
import app.vimusic.android.utils.smoothScrollToTop
import kotlinx.coroutines.launch

@Composable
fun BoxScope.FloatingActionsContainerWithScrollToTop(
    lazyGridState: LazyGridState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    @DrawableRes icon: Int? = null,
    @DrawableRes scrollIcon: Int? = R.drawable.chevron_up,
    onClick: (() -> Unit)? = null,
    onScrollToTop: (suspend () -> Unit)? = lazyGridState::smoothScrollToTop,
    reverse: Boolean = false,
    insets: WindowInsets = LocalPlayerAwareWindowInsets.current
) = FloatingActions(
    state = if (visible) lazyGridState.scrollingInfo() else null,
    onScrollToTop = onScrollToTop,
    reverse = reverse,
    icon = icon,
    scrollIcon = scrollIcon,
    onClick = onClick,
    insets = insets,
    modifier = modifier
)

@Composable
fun BoxScope.FloatingActionsContainerWithScrollToTop(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    @DrawableRes icon: Int? = null,
    @DrawableRes scrollIcon: Int? = R.drawable.chevron_up,
    onClick: (() -> Unit)? = null,
    onScrollToTop: (suspend () -> Unit)? = lazyListState::smoothScrollToTop,
    reverse: Boolean = false,
    insets: WindowInsets = LocalPlayerAwareWindowInsets.current
) = FloatingActions(
    state = if (visible) lazyListState.scrollingInfo() else null,
    onScrollToTop = onScrollToTop,
    reverse = reverse,
    icon = icon,
    scrollIcon = scrollIcon,
    onClick = onClick,
    insets = insets,
    modifier = modifier
)

@Composable
fun BoxScope.FloatingActionsContainerWithScrollToTop(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    @DrawableRes icon: Int? = null,
    @DrawableRes scrollIcon: Int? = R.drawable.chevron_up,
    onClick: (() -> Unit)? = null,
    onScrollToTop: (suspend () -> Unit)? = scrollState::smoothScrollToTop,
    reverse: Boolean = false,
    insets: WindowInsets = LocalPlayerAwareWindowInsets.current
) = FloatingActions(
    state = if (visible) scrollState.scrollingInfo() else null,
    onScrollToTop = onScrollToTop,
    reverse = reverse,
    icon = icon,
    scrollIcon = scrollIcon,
    onClick = onClick,
    insets = insets,
    modifier = modifier
)

@Composable
private fun BoxScope.FloatingActions(
    state: ScrollingInfo?,
    insets: WindowInsets,
    modifier: Modifier = Modifier,
    onScrollToTop: (suspend () -> Unit)? = null,
    reverse: Boolean = false,
    @DrawableRes icon: Int? = null,
    @DrawableRes scrollIcon: Int? = R.drawable.chevron_up,
    onClick: (() -> Unit)? = null
) = Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.Bottom,
    modifier = modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp)
        .padding(
            insets
                .only(WindowInsetsSides.End)
                .asPaddingValues()
        )
) {
    val transition = updateTransition(state, "")
    val bottomPaddingValues = insets.only(WindowInsetsSides.Bottom).asPaddingValues()
    val coroutineScope = rememberCoroutineScope()

    onScrollToTop?.let {
        transition.AnimatedVisibility(
            visible = { it != null && it.isScrollingDown == reverse && it.isFar },
            enter = slideInVertically(tween(500, if (icon == null) 0 else 100)) { it },
            exit = slideOutVertically(tween(500, 0)) { it }
        ) {
            SecondaryButton(
                onClick = {
                    coroutineScope.launch { onScrollToTop() }
                },
                iconId = scrollIcon ?: R.drawable.chevron_up,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .padding(bottomPaddingValues)
            )
        }
    }

    icon?.let {
        onClick?.let {
            transition.AnimatedVisibility(
                visible = { it?.isScrollingDown == false },
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 500, delayMillis = 0),
                    initialOffsetY = { it }
                ),
                exit = slideOutVertically(
                    animationSpec = tween(durationMillis = 500, delayMillis = 100),
                    targetOffsetY = { it }
                )
            ) {
                PrimaryButton(
                    icon = icon,
                    onClick = onClick,
                    enabled = transition.targetState?.isScrollingDown == false,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .padding(bottomPaddingValues)
                )
            }
        }
    }
}

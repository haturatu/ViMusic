package app.vimusic.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.times
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.ui.modifiers.pressable

val LocalMenuState = staticCompositionLocalOf { MenuState() }

@Stable
class MenuState {
    var isDisplayed by mutableStateOf(false)
        private set

    var content by mutableStateOf<@Composable () -> Unit>({})
        private set

    fun display(content: @Composable () -> Unit) {
        this.content = content
        isDisplayed = true
    }

    fun hide() {
        isDisplayed = false
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState = LocalMenuState.current
) = BoxWithConstraints(modifier = modifier) {
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val height = 0.8f * maxHeight

    val bottomSheetState = rememberBottomSheetState(
        dismissedBound = -windowInsets
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding(),
        expandedBound = height
    )

    LaunchedEffect(state.isDisplayed) {
        if (state.isDisplayed) bottomSheetState.expandSoft()
        else bottomSheetState.dismissSoft()
    }

    LaunchedEffect(bottomSheetState.collapsed) {
        if (bottomSheetState.collapsed) state.hide()
    }

    AnimatedVisibility(
        visible = state.isDisplayed,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Spacer(
            modifier = Modifier
                .pressable(onRelease = state::hide)
                .alpha(bottomSheetState.progress * 0.5f)
                .background(Color.Black)
                .fillMaxSize()
        )
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        if (!bottomSheetState.dismissed) BottomSheet( // This way the back gesture gets handled correctly
            state = bottomSheetState,
            collapsedContent = { },
            onDismiss = { state.hide() },
            indication = null,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .sizeIn(maxHeight = height)
                    .nestedScroll(bottomSheetState.preUpPostDownNestedScrollConnection)
            ) {
                state.content()
            }
        }
    }
}

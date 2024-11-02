package app.vimusic.android.ui.screens.settings

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.R
import app.vimusic.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vimusic.android.ui.components.themed.Scaffold
import app.vimusic.android.ui.screens.GlobalRoutes
import app.vimusic.android.ui.screens.Route
import app.vimusic.android.utils.Logcat
import app.vimusic.android.utils.color
import app.vimusic.android.utils.logcat
import app.vimusic.android.utils.semiBold
import app.vimusic.android.utils.smoothScrollToTop
import app.vimusic.compose.routing.RouteHandler
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.primaryButton
import app.vimusic.core.ui.utils.ActivityIntentBundleAccessor
import kotlinx.coroutines.delay

@Route
@Composable
fun LogsScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()
    val (tabIndex, onTabChanged) = rememberSaveable { mutableIntStateOf(0) }

    RouteHandler {
        GlobalRoutes()

        Content {
            Scaffold(
                key = "logs",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = tabIndex,
                onTabChange = onTabChanged,
                tabColumnContent = {
                    tab(0, R.string.logs, R.drawable.library)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> LogsList()
                    }
                }
            }
        }
    }
}

@Composable
fun LogsList(modifier: Modifier = Modifier) = Box(modifier = modifier.fillMaxSize()) {
    val logs = logcat()
    val state = rememberLazyListState()

    val (_, typography) = LocalAppearance.current
    val context = LocalContext.current

    var initial by remember { mutableStateOf(true) }
    val firstVisibleItemIndex by remember { derivedStateOf { state.firstVisibleItemIndex } }

    LaunchedEffect(logs.size) {
        if (initial && logs.isNotEmpty()) {
            delay(200)
            state.scrollToItem(0)
            initial = false
            return@LaunchedEffect
        }
        if (logs.isEmpty() || firstVisibleItemIndex > 1) return@LaunchedEffect

        state.smoothScrollToTop()
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .asPaddingValues(),
        reverseLayout = true
    ) {
        fun get(i: Int) = logs[logs.size - i - 1]

        items(
            count = logs.size,
            key = { get(it).id }
        ) { i ->
            when (val line = get(i)) {
                is Logcat.FormattedLine -> FormattedLine(
                    line = line,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                )

                is Logcat.RawLine -> BasicText(
                    text = line.raw,
                    style = typography.xs,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    FloatingActionsContainerWithScrollToTop(
        lazyListState = state,
        scrollIcon = R.drawable.chevron_down,
        icon = R.drawable.share_social,
        onClick = {
            val extras = ActivityIntentBundleAccessor.bundle {
                text = logs.joinToString(separator = "\n") {
                    when (it) {
                        is Logcat.FormattedLine ->
                            "[${it.timestamp}] ${it.level.name} (${it.pid}) ${it.tag} - ${it.message}"

                        is Logcat.RawLine -> it.raw
                    }
                }
            }

            context.startActivity(
                Intent(Intent.ACTION_SEND).apply {
                    putExtras(extras)
                    type = "text/plain"
                }.let { Intent.createChooser(it, null) }
            )
        }
    )
}

@Composable
fun LazyItemScope.FormattedLine(
    line: Logcat.FormattedLine,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography, _, thumbnailShape) = LocalAppearance.current

    val backgroundColor = remember(line, colorPalette) {
        when (line.level) {
            Logcat.FormattedLine.Level.Error -> colorPalette.red
            Logcat.FormattedLine.Level.Warning -> colorPalette.yellow
            Logcat.FormattedLine.Level.Debug -> colorPalette.blue
            Logcat.FormattedLine.Level.Info -> colorPalette.primaryButton
            Logcat.FormattedLine.Level.Unknown -> colorPalette.textDisabled
        }
    }

    var singleLine by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .clip(thumbnailShape)
            .clickable { singleLine = !singleLine }
            .background(backgroundColor)
            .padding(16.dp)
            .animateItem()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(
                    when (line.level) {
                        Logcat.FormattedLine.Level.Error -> R.drawable.alert_circle
                        Logcat.FormattedLine.Level.Warning -> R.drawable.warning_outline
                        Logcat.FormattedLine.Level.Debug -> R.drawable.bug_outline
                        Logcat.FormattedLine.Level.Info -> R.drawable.information_circle_outline
                        Logcat.FormattedLine.Level.Unknown -> R.drawable.help_outline
                    }
                ),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColorFor(backgroundColor))
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    text = line.timestamp.toString(),
                    style = typography.xxs.color(contentColorFor(backgroundColor)),
                    modifier = Modifier.padding(start = 8.dp)
                )
                BasicText(
                    text = line.tag,
                    style = typography.xxs.semiBold.color(
                        contentColorFor(
                            backgroundColor
                        )
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        AnimatedContent(
            targetState = singleLine,
            label = "",
            transitionSpec = { EnterTransition.None togetherWith fadeOut() }
        ) { currentSingleLine ->
            BasicText(
                text = line.message,
                style = typography.xs.color(contentColorFor(backgroundColor)),
                modifier = Modifier.padding(top = 8.dp),
                maxLines = if (currentSingleLine) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

package app.vimusic.android.ui.screens.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.R
import app.vimusic.android.ui.components.ShimmerHost
import app.vimusic.android.ui.components.themed.Header
import app.vimusic.android.ui.components.themed.HeaderPlaceholder
import app.vimusic.android.ui.items.SongItemPlaceholder
import app.vimusic.android.ui.screens.home.MoodItem
import app.vimusic.android.utils.semiBold
import app.vimusic.compose.persist.persist
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.BrowseBody
import app.vimusic.providers.innertube.requests.BrowseResult
import app.vimusic.providers.innertube.requests.browse
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList

private const val DEFAULT_BROWSE_ID = "FEmusic_moods_and_genres"

@Composable
fun MoreMoodsList(
    onMoodClick: (mood: Innertube.Mood.Item) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    val (colorPalette, typography) = LocalAppearance.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    var moodsPage by persist<BrowseResult>(tag = "more_moods/list")
    val data by remember {
        derivedStateOf {
            moodsPage?.items?.map {
                it.title.orEmpty() to it.items.filterIsInstance<Innertube.Mood.Item>().toImmutableList()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (moodsPage != null) return@LaunchedEffect

        moodsPage = Innertube
            .browse(BrowseBody(browseId = DEFAULT_BROWSE_ID))
            ?.also { it.exceptionOrNull()?.printStackTrace() }
            ?.getOrNull()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = windowInsets
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .asPaddingValues(),
        modifier = modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        item(
            key = "header",
            contentType = 0,
            span = { GridItemSpan(columns) }
        ) {
            if (moodsPage == null) HeaderPlaceholder(modifier = Modifier.shimmer())
            else Header(
                title = stringResource(R.string.moods_and_genres),
                modifier = Modifier.padding(endPaddingValues)
            )
        }

        data?.let { page ->
            if (page.isNotEmpty()) page.fastForEachIndexed { i, (title, moods) ->
                item(
                    key = "header:$i,$title",
                    contentType = 0,
                    span = { GridItemSpan(columns) }
                ) {
                    BasicText(
                        text = title,
                        style = typography.m.semiBold,
                        modifier = sectionTextModifier
                    )
                }

                itemsIndexed(
                    items = moods,
                    key = { j, item -> "item:$j,${item.key}" }
                ) { _, mood ->
                    MoodItem(
                        mood = mood,
                        onClick = { mood.endpoint.browseId?.let { _ -> onMoodClick(mood) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    )
                }
            }
        }

        if (moodsPage == null) item(
            key = "loading",
            contentType = 0,
            span = { GridItemSpan(columns) }
        ) {
            ShimmerHost(modifier = Modifier.fillMaxWidth()) {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }
            }
        }
    }
}

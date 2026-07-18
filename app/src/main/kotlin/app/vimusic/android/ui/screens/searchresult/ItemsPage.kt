package app.vimusic.android.ui.screens.searchresult

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.util.Log
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.R
import app.vimusic.android.ui.components.ShimmerHost
import app.vimusic.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vimusic.android.ui.components.themed.RetryMessage
import app.vimusic.android.ui.state.LoadPhase
import app.vimusic.android.ui.state.PagedLoadState
import app.vimusic.android.utils.center
import app.vimusic.android.utils.secondary
import app.vimusic.android.utils.requireValue
import app.vimusic.android.utils.runSuspendCatching
import app.vimusic.compose.persist.persist
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
inline fun <T : YoutubeMusicInnertube.Item> ItemsPage(
    tag: String,
    crossinline header: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = stringResource(R.string.no_items_found),
    noinline provider: (suspend (String?) -> Result<YoutubeMusicInnertube.ItemsPage<T>?>?)? = null
) = ItemsPage(
    tag = tag,
    header = { before, _ -> header(before) },
    itemContent = itemContent,
    itemPlaceholderContent = itemPlaceholderContent,
    modifier = modifier,
    initialPlaceholderCount = initialPlaceholderCount,
    continuationPlaceholderCount = continuationPlaceholderCount,
    emptyItemsText = emptyItemsText,
    provider = provider
)

@Composable
inline fun <T : YoutubeMusicInnertube.Item> ItemsPage(
    tag: String,
    crossinline header: @Composable (
        beforeContent: (@Composable () -> Unit)?,
        afterContent: (@Composable () -> Unit)?
    ) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = stringResource(R.string.no_items_found),
    noinline provider: (suspend (String?) -> Result<YoutubeMusicInnertube.ItemsPage<T>?>?)? = null
) {
    val (_, typography) = LocalAppearance.current
    val updatedProvider by rememberUpdatedState(provider)
    val lazyListState = rememberLazyListState()
    var itemsPage by persist<YoutubeMusicInnertube.ItemsPage<T>?>(tag)
    var pageState by remember(tag) {
        mutableStateOf(
            PagedLoadState(
                items = itemsPage?.items.orEmpty(),
                continuation = itemsPage?.continuation,
                initialLoad = if (itemsPage == null) LoadPhase.Idle else LoadPhase.Complete,
                appendLoad = if (itemsPage != null && itemsPage?.continuation == null) {
                    LoadPhase.Complete
                } else {
                    LoadPhase.Idle
                },
            )
        )
    }
    var retryGeneration by remember(tag) { mutableStateOf(0) }

    val shouldLoad by remember {
        derivedStateOf {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }
    }

    val providerReady = provider != null
    val continuation = pageState.continuation
    LaunchedEffect(tag, providerReady, continuation, retryGeneration) {
        if (!providerReady || pageState.isLoading || pageState.isComplete) return@LaunchedEffect
        snapshotFlow { shouldLoad }.filter { it }.first()
        val provideItems = updatedProvider ?: return@LaunchedEffect
        pageState = pageState.startLoading()
        Log.d("SearchItemsPage", "request started tag=$tag continuation=${continuation != null}")
        val result = runSuspendCatching {
            provideItems(continuation).requireValue(
                nullResultMessage = "Page request was not executed for $tag",
                nullValueMessage = "Page response was empty for $tag",
            ).getOrThrow()
        }
        result.fold(
            onSuccess = { loadedPage ->
                pageState = pageState.append(
                    newItems = loadedPage.items.orEmpty(),
                    nextContinuation = loadedPage.continuation,
                )
                itemsPage = YoutubeMusicInnertube.ItemsPage(
                    items = pageState.items,
                    continuation = pageState.continuation,
                )
            },
            onFailure = { error ->
                Log.w("SearchItemsPage", "load failed for tag=$tag", error)
                pageState = pageState.fail(error)
            }
        )
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = "header"
            ) {
                header(null, null)
            }

            items(
                items = pageState.items,
                key = YoutubeMusicInnertube.Item::key,
                itemContent = itemContent
            )

            if (pageState.initialLoad == LoadPhase.Complete && pageState.items.isEmpty()) item(key = "empty") {
                BasicText(
                    text = emptyItemsText,
                    style = typography.xs.secondary.center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 32.dp)
                        .fillMaxWidth()
                )
            }

            if (!pageState.hasError && !pageState.isComplete) item(key = "loading") {
                val isFirstLoad = pageState.items.isEmpty()

                ShimmerHost(
                    modifier = if (isFirstLoad) Modifier.fillParentMaxSize() else Modifier
                ) {
                    repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                        itemPlaceholderContent()
                    }
                }
            }

            if (pageState.hasError) item(key = "load_error") {
                RetryMessage(
                    onRetry = {
                        pageState = pageState.retry()
                        retryGeneration++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

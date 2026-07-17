@file:Suppress("TooGenericExceptionCaught") // Provider implementations may throw library-specific failures.

package app.vimusic.android.ui.screens.searchresult

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
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
import app.vimusic.android.utils.center
import app.vimusic.android.utils.secondary
import app.vimusic.android.utils.requireValue
import app.vimusic.compose.persist.persist
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.providers.youtubemusic.innertube.YoutubeMusicInnertube
import app.vimusic.providers.youtubemusic.innertube.utils.plus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

sealed interface PageLoadStatus {
    data object Idle : PageLoadStatus
    data object Loading : PageLoadStatus
    data object Complete : PageLoadStatus
    data class Error(val throwable: Throwable) : PageLoadStatus
}

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
    var loadStatus by remember(tag) {
        mutableStateOf<PageLoadStatus>(
            if (itemsPage?.continuation == null && itemsPage != null) PageLoadStatus.Complete
            else PageLoadStatus.Idle
        )
    }
    var retryGeneration by remember(tag) { mutableStateOf(0) }

    val shouldLoad by remember {
        derivedStateOf {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }
    }

    val providerReady = provider != null
    val continuation = itemsPage?.continuation
    LaunchedEffect(tag, providerReady, continuation, retryGeneration) {
        if (!providerReady || loadStatus is PageLoadStatus.Loading) return@LaunchedEffect
        snapshotFlow { shouldLoad }.filter { it }.first()
        val provideItems = updatedProvider ?: return@LaunchedEffect
        loadStatus = PageLoadStatus.Loading
        Log.d("SearchItemsPage", "request started tag=$tag continuation=${continuation != null}")
        val result = try {
            provideItems(continuation).requireValue(
                nullResultMessage = "Page request was not executed for $tag",
                nullValueMessage = "Page response was empty for $tag",
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
        result.fold(
            onSuccess = { loadedPage ->
                itemsPage = if (itemsPage == null) loadedPage else itemsPage + loadedPage
                loadStatus = if (loadedPage.continuation == null) {
                    PageLoadStatus.Complete
                } else {
                    PageLoadStatus.Idle
                }
            },
            onFailure = { error ->
                Log.w("SearchItemsPage", "load failed for tag=$tag", error)
                loadStatus = PageLoadStatus.Error(error)
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
                items = itemsPage?.items ?: emptyList(),
                key = YoutubeMusicInnertube.Item::key,
                itemContent = itemContent
            )

            if (itemsPage != null && itemsPage?.items.isNullOrEmpty()) item(key = "empty") {
                BasicText(
                    text = emptyItemsText,
                    style = typography.xs.secondary.center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 32.dp)
                        .fillMaxWidth()
                )
            }

            if (loadStatus !is PageLoadStatus.Error && !(itemsPage != null && itemsPage?.continuation == null)) item(key = "loading") {
                val isFirstLoad = itemsPage?.items.isNullOrEmpty()

                ShimmerHost(
                    modifier = if (isFirstLoad) Modifier.fillParentMaxSize() else Modifier
                ) {
                    repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                        itemPlaceholderContent()
                    }
                }
            }

            if (loadStatus is PageLoadStatus.Error) item(key = "load_error") {
                BasicText(
                    text = stringResource(R.string.error_message),
                    style = typography.s.secondary.center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            loadStatus = PageLoadStatus.Idle
                            retryGeneration++
                        }
                        .padding(horizontal = 16.dp, vertical = 32.dp)
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

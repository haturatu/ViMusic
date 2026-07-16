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
import app.vimusic.android.utils.center
import app.vimusic.android.utils.secondary
import app.vimusic.compose.persist.persist
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.providers.newpipe.NewPipeMusic
import app.vimusic.providers.newpipe.utils.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
inline fun <T : NewPipeMusic.Item> ItemsPage(
    tag: String,
    crossinline header: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = stringResource(R.string.no_items_found),
    noinline provider: (suspend (String?) -> Result<NewPipeMusic.ItemsPage<T>?>?)? = null
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
inline fun <T : NewPipeMusic.Item> ItemsPage(
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
    noinline provider: (suspend (String?) -> Result<NewPipeMusic.ItemsPage<T>?>?)? = null
) {
    val (_, typography) = LocalAppearance.current
    val updatedProvider by rememberUpdatedState(provider)
    val lazyListState = rememberLazyListState()
    var itemsPage by persist<NewPipeMusic.ItemsPage<T>?>(tag)
    // This scope deliberately has its own Job. LazyColumn can temporarily
    // dispose/recompose its loading item while a request is running; tying the
    // request to LaunchedEffect would cancel a successful HTTP response.
    val requestScope = remember(tag) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    var requestInFlight by remember(tag) { mutableStateOf(false) }

    val shouldLoad by remember {
        derivedStateOf {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }
    }

    // `shouldLoad` is driven by LazyColumn's layout pass and can temporarily
    // switch back to false while a response is in flight. Do not use it as an
    // effect key: that cancels the request and leaves the placeholder visible.
    // The stable query/tab [tag] owns this collector; `shouldLoad` only starts
    // the next page load.
    LaunchedEffect(tag) {
        snapshotFlow { shouldLoad }
            .filter { it }
            .collect {
                if (requestInFlight) return@collect
                val provideItems = updatedProvider ?: return@collect
                requestInFlight = true
                val continuation = itemsPage?.continuation

                requestScope.launch {
                    Log.d("SearchItemsPage", "request started tag=$tag continuation=${continuation != null}")
                    val result = try {
                        provideItems(continuation)
                    } catch (error: Throwable) {
                        Log.e("SearchItemsPage", "provider threw for tag=$tag", error)
                        null
                    }
                    Log.d("SearchItemsPage", "provider returned tag=$tag result=${result != null}")
                    withContext(Dispatchers.Main.immediate) {
                        requestInFlight = false
                        result?.onSuccess {
                            Log.d(
                                "SearchItemsPage",
                                "loaded tag=$tag items=${it?.items?.size ?: 0} continuation=${it?.continuation != null}"
                            )
                            if (it == null) {
                                itemsPage = (itemsPage ?: NewPipeMusic.ItemsPage(null, null))
                                    .copy(continuation = null)
                            } else itemsPage += it
                        }?.onFailure {
                            Log.w("SearchItemsPage", "load failed for tag=$tag", it)
                            itemsPage = itemsPage?.copy(continuation = null)
                        }?.exceptionOrNull()?.printStackTrace()
                    }
                }
            }
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
                key = NewPipeMusic.Item::key,
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

            if (!(itemsPage != null && itemsPage?.continuation == null)) item(key = "loading") {
                val isFirstLoad = itemsPage?.items.isNullOrEmpty()

                ShimmerHost(
                    modifier = if (isFirstLoad) Modifier.fillParentMaxSize() else Modifier
                ) {
                    repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                        itemPlaceholderContent()
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

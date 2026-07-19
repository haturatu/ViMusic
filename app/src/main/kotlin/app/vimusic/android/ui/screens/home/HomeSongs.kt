package app.vimusic.android.ui.screens.home

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.vimusic.android.LocalAppContainer
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.OrderPreferences
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vimusic.android.ui.components.themed.Header
import app.vimusic.android.ui.components.themed.HeaderIconButton
import app.vimusic.android.ui.components.themed.HideSongDialog
import app.vimusic.android.ui.components.themed.InHistoryMediaItemMenu
import app.vimusic.android.ui.components.themed.TextField
import app.vimusic.android.ui.items.SongItem
import app.vimusic.android.ui.items.SongTotalPlayTimeOverlay
import app.vimusic.android.ui.modifiers.songSwipeActions
import app.vimusic.android.ui.screens.Route
import app.vimusic.android.utils.LocalPlaybackActions
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.forcePlayAtIndex
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeSongs(
    onSearchClick: () -> Unit
) = with(OrderPreferences) {
    val songsRepository = LocalAppContainer.current.songsRepository

    HomeSongs(
        onSearchClick = onSearchClick,
        songProvider = { query ->
            songsRepository.pagedSongs(
                sortBy = songSortBy,
                sortOrder = songSortOrder,
                onlyPlayed = true,
                searchQuery = query
            )
        },
        allSongsProvider = { query ->
            songsRepository.songs(
                sortBy = songSortBy,
                sortOrder = songSortOrder,
                onlyPlayed = true,
                searchQuery = query
            )
        },
        onHideSong = songsRepository::deleteSong,
        sortBy = songSortBy,
        setSortBy = { songSortBy = it },
        sortOrder = songSortOrder,
        setSortOrder = { songSortOrder = it },
        title = stringResource(R.string.songs)
    )
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@OptIn(UnstableApi::class)
@Route
@Composable
fun HomeSongs(
    onSearchClick: () -> Unit,
    songProvider: (String?) -> Flow<PagingData<Song>>,
    allSongsProvider: suspend (String?) -> List<Song>,
    onHideSong: (Song) -> Unit,
    sortBy: SongSortBy,
    setSortBy: (SongSortBy) -> Unit,
    sortOrder: SortOrder,
    setSortOrder: (SortOrder) -> Unit,
    title: String
) {
    val (colorPalette) = LocalAppearance.current

    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val playbackActions = LocalPlaybackActions.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var filter: String? by rememberSaveable { mutableStateOf(null) }
    var searchQuery: String? by rememberSaveable { mutableStateOf(null) }
    var hidingSong: String? by rememberSaveable { mutableStateOf(null) }
    LaunchedEffect(filter) {
        delay(200)
        searchQuery = filter?.trim()?.takeIf(String::isNotEmpty)
    }
    val songs = remember(sortBy, sortOrder, searchQuery) {
        songProvider(searchQuery)
    }.collectAsLazyPagingItems()

    val lazyListState = rememberLazyListState()

    Box(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Header(title = title) {
                    var searching by rememberSaveable { mutableStateOf(false) }

                    AnimatedContent(
                        targetState = searching,
                        label = ""
                    ) { state ->
                        if (state) {
                            val focusRequester = remember { FocusRequester() }

                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }

                            TextField(
                                value = filter.orEmpty(),
                                onValueChange = { filter = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (filter.isNullOrBlank()) filter = ""
                                    focusManager.clearFocus()
                                }),
                                hintText = stringResource(R.string.filter_placeholder),
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (!it.hasFocus) {
                                            keyboardController?.hide()
                                            if (filter?.isBlank() == true) {
                                                filter = null
                                                searching = false
                                            }
                                        }
                                    }
                            )
                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                            HeaderIconButton(
                                onClick = { searching = true },
                                icon = R.drawable.search,
                                color = colorPalette.text
                            )

                            // The former count required retaining every Song in memory. Paging loads on demand.
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (!searchQuery.isNullOrBlank()) HeaderIconButton(
                        icon = R.drawable.enqueue,
                        onClick = {
                            coroutineScope.launch {
                                playbackActions.enqueue(
                                    allSongsProvider(searchQuery).map(Song::asMediaItem)
                                )
                            }
                        }
                    )

                    HeaderSongSortBy(sortBy, setSortBy, sortOrder, setSortOrder)
                }
            }

            items(
                count = songs.itemCount,
                key = songs.itemKey(Song::id)
            ) { index ->
                val song = songs[index] ?: return@items
                if (hidingSong == song.id) HideSongDialog(
                    song = song,
                    onDismiss = { hidingSong = null },
                    onConfirm = {
                        hidingSong = null
                        menuState.hide()
                    },
                    onHideSong = onHideSong
                )

                SongItem(
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                keyboardController?.hide()
                                menuState.display {
                                    InHistoryMediaItemMenu(
                                        song = song,
                                        onDismiss = menuState::hide,
                                        onHideFromDatabase = { hidingSong = song.id }
                                    )
                                }
                            },
                            onClick = {
                                keyboardController?.hide()
                                coroutineScope.launch {
                                    binder?.stopRadio()
                                    val queue = allSongsProvider(searchQuery)
                                    val targetIndex = queue.indexOfFirst { it.id == song.id }
                                    if (targetIndex >= 0) binder?.player?.forcePlayAtIndex(
                                        queue.map(Song::asMediaItem),
                                        targetIndex
                                    )
                                }
                            }
                        )
                        .animateItem()
                        .songSwipeActions(
                            key = songs.itemSnapshotList.items,
                            mediaItem = song.asMediaItem,
                            songToHide = song,
                            onSwipeLeftRequested = { hidingSong = it.id },
                            onHideSong = onHideSong
                        ),
                    song = song,
                    thumbnailSize = Dimensions.thumbnails.song,
                    onThumbnailContent = if (sortBy == SongSortBy.PlayTime) {
                        { SongTotalPlayTimeOverlay(song.totalPlayTimeMs) }
                    } else null
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState,
            icon = R.drawable.search,
            onClick = onSearchClick
        )
    }
}

// Row content, for convenience, doesn't need modifier/receiver
@Suppress("UnusedReceiverParameter", "ModifierMissing")
@Composable
fun RowScope.HeaderSongSortBy(
    sortBy: SongSortBy,
    setSortBy: (SongSortBy) -> Unit,
    sortOrder: SortOrder,
    setSortOrder: (SortOrder) -> Unit
) {
    val (colorPalette) = LocalAppearance.current

    val sortOrderIconRotation by animateFloatAsState(
        targetValue = if (sortOrder == SortOrder.Ascending) 0f else 180f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = ""
    )

    HeaderIconButton(
        icon = R.drawable.trending,
        enabled = sortBy == SongSortBy.PlayTime,
        onClick = { setSortBy(SongSortBy.PlayTime) }
    )

    HeaderIconButton(
        icon = R.drawable.text,
        enabled = sortBy == SongSortBy.Title,
        onClick = { setSortBy(SongSortBy.Title) }
    )

    HeaderIconButton(
        icon = R.drawable.time,
        enabled = sortBy == SongSortBy.DateAdded,
        onClick = { setSortBy(SongSortBy.DateAdded) }
    )

    HeaderIconButton(
        icon = R.drawable.arrow_up,
        color = colorPalette.text,
        onClick = { setSortOrder(!sortOrder) },
        modifier = Modifier.graphicsLayer { rotationZ = sortOrderIconRotation }
    )
}

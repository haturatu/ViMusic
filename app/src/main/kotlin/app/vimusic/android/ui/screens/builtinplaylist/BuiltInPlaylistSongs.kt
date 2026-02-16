package app.vimusic.android.ui.screens.builtinplaylist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vimusic.android.LocalAppContainer
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vimusic.android.ui.components.themed.Header
import app.vimusic.android.ui.components.themed.HideSongDialog
import app.vimusic.android.ui.components.themed.InHistoryMediaItemMenu
import app.vimusic.android.ui.components.themed.NonQueuedMediaItemMenu
import app.vimusic.android.ui.components.themed.SecondaryTextButton
import app.vimusic.android.ui.components.themed.SongListActionsRow
import app.vimusic.android.ui.components.themed.ValueSelectorDialog
import app.vimusic.android.ui.items.SongItem
import app.vimusic.android.ui.modifiers.songSwipeActions
import app.vimusic.android.ui.screens.home.HeaderSongSortBy
import app.vimusic.android.ui.viewmodels.BuiltInPlaylistSongsViewModel
import app.vimusic.android.utils.LocalPlaybackActions
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.rememberMediaItems
import app.vimusic.compose.persist.persistList
import app.vimusic.core.data.enums.BuiltInPlaylist
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.utils.enumSaver
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BuiltInPlaylistSongs(
    builtInPlaylist: BuiltInPlaylist,
    modifier: Modifier = Modifier
) = with(DataPreferences) {
    val viewModel: BuiltInPlaylistSongsViewModel = viewModel(
        factory = BuiltInPlaylistSongsViewModel.factory(LocalAppContainer.current.builtInPlaylistRepository)
    )
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val playbackActions = LocalPlaybackActions.current

    var songs by persistList<Song>("${builtInPlaylist.name}/songs")
    var hidingSong by rememberSaveable { mutableStateOf<String?>(null) }
    val mediaItems = rememberMediaItems(songs)

    var sortBy by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SongSortBy.DateAdded) }
    var sortOrder by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(SortOrder.Descending) }

    LaunchedEffect(binder, sortBy, sortOrder) {
        viewModel.observeSongs(
            builtInPlaylist = builtInPlaylist,
            sortBy = sortBy,
            sortOrder = sortOrder,
            topPeriodMillis = topListPeriod.duration?.inWholeMilliseconds,
            topLength = topListLength,
            isOfflineCached = { songWithContentLength -> binder?.isCached(songWithContentLength) ?: false }
        ).collect { songs = it.toImmutableList() }
    }

    val lazyListState = rememberLazyListState()

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Header(
                    title = when (builtInPlaylist) {
                        BuiltInPlaylist.Favorites -> stringResource(R.string.favorites)
                        BuiltInPlaylist.Offline -> stringResource(R.string.offline)
                        BuiltInPlaylist.Top -> stringResource(
                            R.string.format_my_top_playlist,
                            topListLength
                        )

                        BuiltInPlaylist.History -> stringResource(R.string.history)
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    SongListActionsRow(
                        mediaItems = mediaItems,
                        showDownload = builtInPlaylist != BuiltInPlaylist.Offline,
                        onEnqueue = { playbackActions.enqueue(mediaItems) },
                        trailingContent = {
                            if (builtInPlaylist.sortable) HeaderSongSortBy(
                                sortBy = sortBy,
                                setSortBy = { sortBy = it },
                                sortOrder = sortOrder,
                                setSortOrder = { sortOrder = it }
                            )

                            if (builtInPlaylist == BuiltInPlaylist.Top) {
                                var dialogShowing by rememberSaveable { mutableStateOf(false) }

                                SecondaryTextButton(
                                    text = topListPeriod.displayName(),
                                    onClick = { dialogShowing = true }
                                )

                                if (dialogShowing) ValueSelectorDialog(
                                    onDismiss = { dialogShowing = false },
                                    title = stringResource(
                                        R.string.format_view_top_of_header,
                                        topListLength
                                    ),
                                    selectedValue = topListPeriod,
                                    values = DataPreferences.TopListPeriod.entries.toImmutableList(),
                                    onValueSelect = { topListPeriod = it },
                                    valueText = { it.displayName() }
                                )
                            }
                        }
                    )
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
                contentType = { _, song -> song }
            ) { index, song ->
                if (hidingSong == song.id) HideSongDialog(
                    song = song,
                    onDismiss = { hidingSong = null },
                    onConfirm = { hidingSong = null }
                )

                SongItem(
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                menuState.display {
                                    when (builtInPlaylist) {
                                        BuiltInPlaylist.Offline -> InHistoryMediaItemMenu(
                                            song = song,
                                            onDismiss = menuState::hide
                                        )

                                        BuiltInPlaylist.Favorites,
                                        BuiltInPlaylist.Top,
                                        BuiltInPlaylist.History -> NonQueuedMediaItemMenu(
                                            mediaItem = song.asMediaItem,
                                            onDismiss = menuState::hide
                                        )
                                    }
                                }
                            },
                            onClick = {
                                playbackActions.playAtIndex(mediaItems, index)
                            }
                        )
                        .songSwipeActions(
                            key = songs,
                            mediaItem = song.asMediaItem,
                            songToHide = song,
                            onSwipeLeftRequested = { hidingSong = it.id }
                        )
                        .animateItem(),
                    song = song,
                    index = if (builtInPlaylist == BuiltInPlaylist.Top) index else null,
                    thumbnailSize = Dimensions.thumbnails.song
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                playbackActions.shufflePlay(mediaItems)
            }
        )
    }
}

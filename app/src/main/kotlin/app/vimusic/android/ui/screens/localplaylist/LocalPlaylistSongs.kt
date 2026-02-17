package app.vimusic.android.ui.screens.localplaylist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.themed.CircularProgressIndicator
import app.vimusic.android.ui.components.themed.ConfirmationDialog
import app.vimusic.android.ui.components.themed.Header
import app.vimusic.android.ui.components.themed.HeaderIconButton
import app.vimusic.android.ui.components.themed.HideSongDialog
import app.vimusic.android.ui.components.themed.InPlaylistMediaItemMenu
import app.vimusic.android.ui.components.themed.Menu
import app.vimusic.android.ui.components.themed.MenuEntry
import app.vimusic.android.ui.components.themed.ReorderHandle
import app.vimusic.android.ui.components.themed.SongListActionsRow
import app.vimusic.android.ui.components.themed.SongListScaffold
import app.vimusic.android.ui.components.themed.TextFieldDialog
import app.vimusic.android.ui.items.SongItem
import app.vimusic.android.ui.modifiers.songSwipeActions
import app.vimusic.android.ui.viewmodels.LocalPlaylistViewModel
import app.vimusic.android.utils.LocalPlaybackActions
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.enqueue
import app.vimusic.android.utils.launchYouTubeMusic
import app.vimusic.android.utils.playSongAtIndex
import app.vimusic.android.utils.rememberMediaItems
import app.vimusic.android.utils.shufflePlay
import app.vimusic.android.utils.toast
import app.vimusic.compose.reordering.animateItemPlacement
import app.vimusic.compose.reordering.draggedItem
import app.vimusic.compose.reordering.rememberReorderingState
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.utils.isLandscape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalPlaylistSongs(
    viewModel: LocalPlaylistViewModel,
    playlist: Playlist,
    songs: ImmutableList<Song>,
    onDelete: () -> Unit,
    thumbnailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val playbackActions = LocalPlaybackActions.current
    val menuState = LocalMenuState.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val youtubeMusicNotInstalled = stringResource(R.string.youtube_music_not_installed)

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val mediaItems = rememberMediaItems(songs)

    var loading by remember { mutableStateOf(false) }
    var hidingSong by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (DataPreferences.autoSyncPlaylists) playlist.browseId?.let { browseId ->
            loading = true
            viewModel.sync(playlist, browseId)
            loading = false
        }
    }

    val reorderingState = rememberReorderingState(
        lazyListState = lazyListState,
        key = songs,
        onDragEnd = { fromIndex, toIndex ->
            viewModel.move(playlist.id, fromIndex, toIndex)
        },
        extraItemCount = 1
    )

    var isRenaming by rememberSaveable { mutableStateOf(false) }

    if (isRenaming) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        initialTextInput = playlist.name,
        onDismiss = { isRenaming = false },
        onAccept = { text ->
            viewModel.rename(playlist = playlist, name = text)
        }
    )

    var isDeleting by rememberSaveable { mutableStateOf(false) }

    if (isDeleting) ConfirmationDialog(
        text = stringResource(R.string.confirm_delete_playlist),
        onDismiss = { isDeleting = false },
        onConfirm = {
            viewModel.delete(playlist)
            onDelete()
        }
    )

    LookaheadScope {
        SongListScaffold(
            thumbnailContent = thumbnailContent,
            modifier = modifier,
            listState = reorderingState.lazyListState,
            listBackground = colorPalette.background0,
            shuffleVisible = !reorderingState.isDragging,
            onShuffle = { playbackActions.shufflePlay(mediaItems) },
            headerContent = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Header(
                        title = playlist.name,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        SongListActionsRow(
                            mediaItems = mediaItems,
                            onEnqueue = { playbackActions.enqueue(mediaItems) },
                            leadingContent = {
                                AnimatedVisibility(loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                }
                            },
                            trailingContent = {
                                HeaderIconButton(
                                    icon = R.drawable.ellipsis_horizontal,
                                    color = colorPalette.text,
                                    onClick = {
                                        menuState.display {
                                            Menu {
                                                playlist.browseId?.let { browseId ->
                                                    MenuEntry(
                                                        icon = R.drawable.sync,
                                                        text = stringResource(R.string.sync),
                                                        enabled = !loading,
                                                        onClick = {
                                                            menuState.hide()
                                                            coroutineScope.launch {
                                                                loading = true
                                                                viewModel.sync(playlist, browseId)
                                                                loading = false
                                                            }
                                                        }
                                                    )

                                                    songs.firstOrNull()?.id?.let { firstSongId ->
                                                        MenuEntry(
                                                            icon = R.drawable.play,
                                                            text = stringResource(R.string.watch_playlist_on_youtube),
                                                            onClick = {
                                                                menuState.hide()
                                                                binder?.player?.pause()
                                                                uriHandler.openUri(
                                                                    "https://youtube.com/watch?v=$firstSongId&list=${
                                                                        playlist.browseId.drop(2)
                                                                    }"
                                                                )
                                                            }
                                                        )

                                                        MenuEntry(
                                                            icon = R.drawable.musical_notes,
                                                            text = stringResource(R.string.open_in_youtube_music),
                                                            onClick = {
                                                                menuState.hide()
                                                                binder?.player?.pause()
                                                                if (
                                                                    !launchYouTubeMusic(
                                                                        context = context,
                                                                        endpoint = "watch?v=$firstSongId&list=${
                                                                            playlist.browseId.drop(2)
                                                                        }"
                                                                    )
                                                                ) context.toast(
                                                                    youtubeMusicNotInstalled
                                                                )
                                                            }
                                                        )
                                                    }
                                                }

                                                MenuEntry(
                                                    icon = R.drawable.pencil,
                                                    text = stringResource(R.string.rename),
                                                    onClick = {
                                                        menuState.hide()
                                                        isRenaming = true
                                                    }
                                                )

                                                MenuEntry(
                                                    icon = R.drawable.trash,
                                                    text = stringResource(R.string.delete),
                                                    onClick = {
                                                        menuState.hide()
                                                        isDeleting = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    }

                    if (!isLandscape) thumbnailContent()
                }
            }
        ) {
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
                                    InPlaylistMediaItemMenu(
                                        playlistId = playlist.id,
                                        positionInPlaylist = index,
                                        song = song,
                                        onDismiss = menuState::hide
                                    )
                                }
                            },
                            onClick = {
                                playbackActions.playAtIndex(mediaItems, index)
                            }
                        )
                        .animateItemPlacement(reorderingState)
                        .draggedItem(
                            reorderingState = reorderingState,
                            index = index
                        )
                        .songSwipeActions(
                            key = songs,
                            mediaItem = song.asMediaItem,
                            songToHide = song,
                            onSwipeLeftRequested = { hidingSong = it.id }
                        )
                        .background(colorPalette.background0),
                    song = song,
                    thumbnailSize = Dimensions.thumbnails.song,
                    trailingContent = {
                        ReorderHandle(
                            reorderingState = reorderingState,
                            index = index
                        )
                    },
                    clip = !reorderingState.isDragging
                )
            }
        }
    }
}

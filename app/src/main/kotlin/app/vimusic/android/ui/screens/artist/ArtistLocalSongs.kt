package app.vimusic.android.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vimusic.android.LocalAppContainer
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.ShimmerHost
import app.vimusic.android.ui.components.rememberSongListState
import app.vimusic.android.ui.components.themed.HideSongDialog
import app.vimusic.android.ui.components.themed.NonQueuedMediaItemMenu
import app.vimusic.android.ui.components.themed.SecondaryTextButton
import app.vimusic.android.ui.components.themed.SongListScaffold
import app.vimusic.android.ui.items.SongItem
import app.vimusic.android.ui.items.SongItemPlaceholder
import app.vimusic.android.ui.modifiers.songSwipeActions
import app.vimusic.android.ui.viewmodels.ArtistLocalSongsViewModel
import app.vimusic.android.utils.LocalPlaybackActions
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.rememberMediaItems
import app.vimusic.compose.persist.persist
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.utils.isLandscape

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistLocalSongs(
    browseId: String,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    thumbnailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ArtistLocalSongsViewModel = viewModel(
        key = "artist_local_songs:$browseId",
        factory = ArtistLocalSongsViewModel.factory(
            browseId = browseId,
            repository = LocalAppContainer.current.libraryRepository
        )
    )
    val playbackActions = LocalPlaybackActions.current
    val (colorPalette) = LocalAppearance.current
    val menuState = LocalMenuState.current

    var songs by persist<List<Song>?>("artist/$browseId/localSongs")
    var hidingSong by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.observeSongs().collect { songs = it }
    }

    val lazyListState = rememberLazyListState()
    val listState = rememberSongListState(songs, isLoading = songs == null)
    val mediaItems = if (listState.items.isNotEmpty()) rememberMediaItems(listState.items) else null

    SongListScaffold(
        thumbnailContent = thumbnailContent,
        modifier = modifier,
        listState = lazyListState,
        listBackground = colorPalette.background0,
        onShuffle = { mediaItems?.let(playbackActions::shufflePlay) },
        headerContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                headerContent {
                    SecondaryTextButton(
                        text = stringResource(R.string.enqueue),
                        enabled = listState.items.isNotEmpty(),
                        onClick = {
                            mediaItems?.let(playbackActions::enqueue)
                        }
                    )
                }

                if (!isLandscape) thumbnailContent()
            }
        }
    ) {
        if (!listState.isLoading) {
            itemsIndexed(
                items = listState.items,
                key = { _, song -> song.id }
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
                                    NonQueuedMediaItemMenu(
                                        onDismiss = menuState::hide,
                                        mediaItem = song.asMediaItem
                                    )
                                }
                            },
                            onClick = {
                                mediaItems?.let { items ->
                                    playbackActions.playAtIndex(items, index)
                                }
                            }
                        )
                        .songSwipeActions(
                            key = listState.items,
                            mediaItem = song.asMediaItem,
                            songToHide = song,
                            onSwipeLeftRequested = { hidingSong = it.id }
                        ),
                    song = song,
                    thumbnailSize = Dimensions.thumbnails.song
                )
            }
        } else item(key = "loading") {
            ShimmerHost {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }
            }
        }
    }
}

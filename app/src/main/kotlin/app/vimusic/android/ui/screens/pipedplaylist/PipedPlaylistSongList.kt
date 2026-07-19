package app.vimusic.android.ui.screens.pipedplaylist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
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
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.themed.Header
import app.vimusic.android.ui.components.themed.HeaderPlaceholder
import app.vimusic.android.ui.components.themed.NonQueuedMediaItemMenu
import app.vimusic.android.ui.components.themed.SongListActionsRow
import app.vimusic.android.ui.components.themed.SongCollectionScreen
import app.vimusic.android.ui.components.themed.songCollectionItems
import app.vimusic.android.ui.components.themed.matchesSongCollectionQuery
import app.vimusic.android.ui.components.themed.adaptiveThumbnailContent
import app.vimusic.android.ui.items.SongItem
import app.vimusic.android.ui.modifiers.songSwipeActions
import app.vimusic.android.ui.viewmodels.PipedPlaylistViewModel
import app.vimusic.android.utils.LocalPlaybackActions
import app.vimusic.android.utils.PipedPlaylistVideoMediaItemMapper
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.enqueue
import app.vimusic.android.utils.playSongAtIndex
import app.vimusic.android.utils.rememberMediaItemsOrNull
import app.vimusic.android.utils.shufflePlay
import app.vimusic.compose.persist.persist
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.utils.isLandscape
import app.vimusic.providers.piped.models.Playlist
import app.vimusic.providers.piped.models.Session
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PipedPlaylistSongList(
    session: Session,
    playlistId: UUID,
    modifier: Modifier = Modifier
) {
    val viewModel: PipedPlaylistViewModel = viewModel(
        key = "piped_playlist:$playlistId",
        factory = PipedPlaylistViewModel.factory(LocalAppContainer.current.pipedPlaylistRepository)
    )
    val (colorPalette) = LocalAppearance.current
    val menuState = LocalMenuState.current
    val playbackActions = LocalPlaybackActions.current

    var playlist by persist<Playlist>(tag = "pipedplaylist/$playlistId/playlistPage")
    var filterQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val displayedVideos = playlist?.videos.orEmpty().filter { video ->
        matchesSongCollectionQuery(filterQuery, video.title, video.uploaderName)
    }
    val mediaItems = rememberMediaItemsOrNull(displayedVideos, PipedPlaylistVideoMediaItemMapper)

    LaunchedEffect(Unit) {
        playlist = withContext(Dispatchers.IO) {
            viewModel.fetchPlaylist(
                session = session,
                playlistId = playlistId
            )
        }
    }

    val lazyListState = rememberLazyListState()

    val thumbnailContent = adaptiveThumbnailContent(
        isLoading = playlist == null,
        url = playlist?.thumbnailUrl?.toString()
    )

    SongCollectionScreen(
        thumbnailContent = thumbnailContent,
        modifier = modifier,
        listState = lazyListState,
        listBackground = colorPalette.background0,
        onShuffle = { mediaItems?.let(playbackActions::shufflePlay) },
        headerContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (playlist == null) HeaderPlaceholder(modifier = Modifier.shimmer())
                else Header(title = playlist?.name ?: stringResource(R.string.unknown)) {
                    SongListActionsRow(
                        mediaItems = mediaItems,
                        onEnqueue = { mediaItems?.let(playbackActions::enqueue) },
                        filterQuery = filterQuery,
                        onFilterQueryChange = { filterQuery = it }
                    )
                }

                if (!isLandscape) thumbnailContent()
            }
        }
    ) {
        songCollectionItems(
            items = displayedVideos,
            isLoading = playlist == null,
        ) { index, song ->
            song.asMediaItem?.let { mediaItem ->
                SongItem(
                    song = mediaItem,
                    thumbnailSize = Dimensions.thumbnails.song,
                    modifier = Modifier
                        .combinedClickable(
                            onLongClick = {
                                menuState.display {
                                    NonQueuedMediaItemMenu(
                                        onDismiss = menuState::hide,
                                        mediaItem = mediaItem
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
                            key = playlistId,
                            mediaItem = mediaItem
                        )
                )
            }
        }
    }
}

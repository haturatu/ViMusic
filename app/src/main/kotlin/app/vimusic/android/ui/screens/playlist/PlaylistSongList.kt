package app.vimusic.android.ui.screens.playlist

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vimusic.android.LocalAppContainer
import app.vimusic.android.R
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.ShimmerHost
import app.vimusic.android.ui.components.themed.Header
import app.vimusic.android.ui.components.themed.HeaderIconButton
import app.vimusic.android.ui.components.themed.HeaderPlaceholder
import app.vimusic.android.ui.components.themed.NonQueuedMediaItemMenu
import app.vimusic.android.ui.components.themed.PlaylistInfo
import app.vimusic.android.ui.components.themed.SongListActionsRow
import app.vimusic.android.ui.components.themed.SongListScaffold
import app.vimusic.android.ui.components.themed.TextFieldDialog
import app.vimusic.android.ui.components.themed.adaptiveThumbnailContent
import app.vimusic.android.ui.items.SongItem
import app.vimusic.android.ui.items.SongItemPlaceholder
import app.vimusic.android.ui.modifiers.songSwipeActions
import app.vimusic.android.ui.viewmodels.PlaylistSongListViewModel
import app.vimusic.android.utils.LocalPlaybackActions
import app.vimusic.android.utils.InnertubeSongMediaItemMapper
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.rememberMediaItemsOrNull
import app.vimusic.compose.persist.persist
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.utils.isLandscape
import app.vimusic.providers.innertube.Innertube
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistSongList(
    browseId: String,
    params: String?,
    maxDepth: Int?,
    shouldDedup: Boolean,
    modifier: Modifier = Modifier
) {
    val viewModel: PlaylistSongListViewModel = viewModel(
        key = "playlist:$browseId",
        factory = PlaylistSongListViewModel.factory(LocalAppContainer.current.playlistRepository)
    )
    val (colorPalette) = LocalAppearance.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playbackActions = LocalPlaybackActions.current

    var playlistPage by persist<Innertube.PlaylistOrAlbumPage?>("playlist/$browseId/playlistPage")

    LaunchedEffect(Unit) {
        if (playlistPage != null && playlistPage?.songsPage?.continuation == null) return@LaunchedEffect

        playlistPage = withContext(Dispatchers.IO) {
            viewModel.fetchPlaylistPage(
                browseId = browseId,
                params = params,
                maxDepth = maxDepth,
                shouldDedup = shouldDedup
            )
        }
    }

    var isImportingPlaylist by rememberSaveable { mutableStateOf(false) }

    if (isImportingPlaylist) TextFieldDialog(
        hintText = stringResource(R.string.enter_playlist_name_prompt),
        initialTextInput = playlistPage?.title.orEmpty(),
        onDismiss = { isImportingPlaylist = false },
        onAccept = { text ->
            viewModel.importPlaylist(
                name = text,
                browseId = browseId,
                thumbnailUrl = playlistPage?.thumbnail?.url,
                songs = playlistPage?.songsPage?.items
            )
        }
    )

    val playlistItems = playlistPage?.songsPage?.items
    val mediaItems = rememberMediaItemsOrNull(playlistItems, InnertubeSongMediaItemMapper)

    val headerContent: @Composable () -> Unit = {
        if (playlistPage == null) HeaderPlaceholder(modifier = Modifier.shimmer())
        else Header(title = playlistPage?.title ?: stringResource(R.string.unknown)) {
            SongListActionsRow(
                mediaItems = mediaItems,
                onEnqueue = { mediaItems?.let(playbackActions::enqueue) },
                trailingContent = {
                    HeaderIconButton(
                        icon = R.drawable.add,
                        color = colorPalette.text,
                        onClick = { isImportingPlaylist = true }
                    )

                    HeaderIconButton(
                        icon = R.drawable.share_social,
                        color = colorPalette.text,
                        onClick = {
                            (
                                    playlistPage?.url
                                        ?: "https://music.youtube.com/playlist?list=${
                                            browseId.removePrefix(
                                                "VL"
                                            )
                                        }"
                                    ).let { url ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }

                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                }
                        }
                    )
                }
            )
        }
    }

    val thumbnailContent = adaptiveThumbnailContent(
        isLoading = playlistPage == null,
        url = playlistPage?.thumbnail?.url
    )

    val lazyListState = rememberLazyListState()

    SongListScaffold(
        thumbnailContent = thumbnailContent,
        modifier = modifier,
        listState = lazyListState,
        listBackground = colorPalette.background0,
        onShuffle = { mediaItems?.let(playbackActions::shufflePlay) },
        headerContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                headerContent()
                if (!isLandscape) thumbnailContent()
                PlaylistInfo(playlist = playlistPage)
            }
        }
    ) {
        itemsIndexed(items = playlistItems ?: emptyList()) { index, song ->
            SongItem(
                song = song,
                thumbnailSize = Dimensions.thumbnails.song,
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
                        key = playlistItems ?: emptyList<Innertube.SongItem>(),
                        mediaItem = song.asMediaItem
                    )
            )
        }

        if (playlistPage == null) item(key = "loading") {
            ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }
            }
        }
    }
}

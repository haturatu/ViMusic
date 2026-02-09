package app.vimusic.android.ui.screens.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.ShimmerHost
import app.vimusic.android.ui.components.themed.NonQueuedMediaItemMenu
import app.vimusic.android.ui.components.themed.SecondaryTextButton
import app.vimusic.android.ui.components.themed.SongListScaffold
import app.vimusic.android.ui.items.SongItem
import app.vimusic.android.ui.items.SongItemPlaceholder
import app.vimusic.android.utils.PlaylistDownloadIcon
import app.vimusic.android.utils.LocalPlaybackActions
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.rememberMediaItems
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.utils.isLandscape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumSongs(
    songs: ImmutableList<Song>,
    headerContent: @Composable (
        beforeContent: (@Composable () -> Unit)?,
        afterContent: (@Composable () -> Unit)?
    ) -> Unit,
    thumbnailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    afterHeaderContent: (@Composable () -> Unit)? = null
) {
    val (colorPalette) = LocalAppearance.current
    val playbackActions = LocalPlaybackActions.current
    val menuState = LocalMenuState.current
    val lazyListState = rememberLazyListState()
    val mediaItems = rememberMediaItems(songs)

    SongListScaffold(
        thumbnailContent = thumbnailContent,
        listState = lazyListState,
        modifier = modifier,
        listBackground = colorPalette.background0,
        onShuffle = { playbackActions.shufflePlay(mediaItems) },
        headerContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                headerContent(
                    {
                        SecondaryTextButton(
                            text = stringResource(R.string.enqueue),
                            enabled = songs.isNotEmpty(),
                            onClick = {
                                playbackActions.enqueue(mediaItems)
                            }
                        )
                    },
                    {
                        PlaylistDownloadIcon(
                            songs = mediaItems.toImmutableList()
                        )
                    }
                )

                if (!isLandscape) thumbnailContent()
                afterHeaderContent?.invoke()
            }
        }
    ) {
        itemsIndexed(
            items = songs,
            key = { _, song -> song.id }
        ) { index, song ->
            SongItem(
                song = song,
                index = index,
                thumbnailSize = Dimensions.thumbnails.song,
                modifier = Modifier.combinedClickable(
                    onLongClick = {
                        menuState.display {
                            NonQueuedMediaItemMenu(
                                onDismiss = menuState::hide,
                                mediaItem = song.asMediaItem
                            )
                        }
                    },
                    onClick = {
                        playbackActions.playAtIndex(mediaItems, index)
                    }
                )
            )
        }

        if (songs.isEmpty()) item(key = "loading") {
            ShimmerHost(modifier = Modifier.fillParentMaxSize()) {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }
            }
        }
    }
}

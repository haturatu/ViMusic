package app.vimusic.android.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import app.vimusic.android.Database
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.repositories.toFtsPrefixQuery
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vimusic.android.ui.components.themed.Header
import app.vimusic.android.ui.components.themed.HideSongDialog
import app.vimusic.android.ui.components.themed.InHistoryMediaItemMenu
import app.vimusic.android.ui.components.themed.SecondaryTextButton
import app.vimusic.android.ui.items.SongItem
import app.vimusic.android.ui.modifiers.songSwipeActions
import app.vimusic.android.utils.align
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.forcePlay
import app.vimusic.android.utils.medium
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.providers.innertube.models.NavigationEndpoint
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalSongSearch(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    var hidingSong by rememberSaveable { mutableStateOf<String?>(null) }
    val query = textFieldValue.text.trim()
    val songs = remember(query) {
        if (query.length > 1) Pager(PagingConfig(pageSize = 50, enablePlaceholders = false)) {
            Database.search(query.toFtsPrefixQuery())
        }.flow else flowOf(PagingData.empty())
    }.collectAsLazyPagingItems()

    val lazyListState = rememberLazyListState()

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Header(
                    titleContent = {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = onTextFieldValueChange,
                            textStyle = typography.xxl.medium.align(TextAlign.End),
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            cursorBrush = SolidColor(colorPalette.text),
                            decorationBox = decorationBox
                        )
                    },
                    actionsContent = {
                        if (textFieldValue.text.isNotEmpty()) SecondaryTextButton(
                            text = stringResource(R.string.clear),
                            onClick = { onTextFieldValueChange(TextFieldValue()) }
                        )
                    }
                )
            }

            items(
                count = songs.itemCount,
                key = songs.itemKey(Song::id)
            ) { index ->
                val song = songs[index] ?: return@items
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
                                    InHistoryMediaItemMenu(
                                        song = song,
                                        onDismiss = menuState::hide
                                    )
                                }
                            },
                            onClick = {
                                val mediaItem = song.asMediaItem
                                binder?.stopRadio()
                                binder?.player?.forcePlay(mediaItem)
                                binder?.setupRadio(
                                    NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId)
                                )
                            }
                        )
                        .songSwipeActions(
                            key = songs.itemSnapshotList.items,
                            mediaItem = song.asMediaItem,
                            songToHide = song,
                            onSwipeLeftRequested = { hidingSong = it.id }
                        )
                        .animateItem(),
                    song = song,
                    thumbnailSize = Dimensions.thumbnails.song
                )
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}

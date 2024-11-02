package app.vimusic.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.vimusic.android.Database
import app.vimusic.android.R
import app.vimusic.android.models.Playlist
import app.vimusic.android.models.SongPlaylistMap
import app.vimusic.android.preferences.AppearancePreferences
import app.vimusic.android.preferences.PlayerPreferences
import app.vimusic.android.service.PlayerService
import app.vimusic.android.transaction
import app.vimusic.android.ui.components.BottomSheet
import app.vimusic.android.ui.components.BottomSheetState
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.MusicBars
import app.vimusic.android.ui.components.themed.BaseMediaItemMenu
import app.vimusic.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vimusic.android.ui.components.themed.HorizontalDivider
import app.vimusic.android.ui.components.themed.IconButton
import app.vimusic.android.ui.components.themed.Menu
import app.vimusic.android.ui.components.themed.MenuEntry
import app.vimusic.android.ui.components.themed.QueuedMediaItemMenu
import app.vimusic.android.ui.components.themed.ReorderHandle
import app.vimusic.android.ui.components.themed.SecondaryTextButton
import app.vimusic.android.ui.components.themed.TextFieldDialog
import app.vimusic.android.ui.components.themed.TextToggle
import app.vimusic.android.ui.items.SongItem
import app.vimusic.android.ui.items.SongItemPlaceholder
import app.vimusic.android.ui.modifiers.swipeToClose
import app.vimusic.android.utils.DisposableListener
import app.vimusic.android.utils.addNext
import app.vimusic.android.utils.asMediaItem
import app.vimusic.android.utils.enqueue
import app.vimusic.android.utils.medium
import app.vimusic.android.utils.onFirst
import app.vimusic.android.utils.semiBold
import app.vimusic.android.utils.shouldBePlaying
import app.vimusic.android.utils.shuffleQueue
import app.vimusic.android.utils.smoothScrollToTop
import app.vimusic.android.utils.windows
import app.vimusic.compose.persist.persist
import app.vimusic.compose.reordering.animateItemPlacement
import app.vimusic.compose.reordering.draggedItem
import app.vimusic.compose.reordering.rememberReorderingState
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.core.data.enums.SortOrder
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.onOverlay
import app.vimusic.core.ui.utils.roundedShape
import app.vimusic.providers.innertube.Innertube
import app.vimusic.providers.innertube.models.bodies.NextBody
import app.vimusic.providers.innertube.requests.nextPage
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Queue(
    layoutState: BottomSheetState,
    binder: PlayerService.Binder,
    beforeContent: @Composable RowScope.() -> Unit,
    afterContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp
    ),
    scrollConnection: NestedScrollConnection = remember(layoutState::preUpPostDownNestedScrollConnection),
    windowInsets: WindowInsets = WindowInsets.systemBars
) {
    val (colorPalette, typography, _, thumbnailShape) = LocalAppearance.current
    val menuState = LocalMenuState.current

    val horizontalBottomPaddingValues = windowInsets
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        .asPaddingValues()

    var suggestions by persist<Result<List<MediaItem>?>?>(tag = "queue/suggestions")

    var mediaItemIndex by remember {
        mutableIntStateOf(if (binder.player.mediaItemCount == 0) -1 else binder.player.currentMediaItemIndex)
    }

    var windows by remember { mutableStateOf(binder.player.currentTimeline.windows) }
    var shouldBePlaying by remember { mutableStateOf(binder.player.shouldBePlaying) }

    val lazyListState = rememberLazyListState()
    val reorderingState = rememberReorderingState(
        lazyListState = lazyListState,
        key = windows,
        onDragEnd = binder.player::moveMediaItem
    )

    val visibleSuggestions by remember {
        derivedStateOf {
            suggestions
                ?.getOrNull()
                .orEmpty()
                .filter { windows.none { window -> window.mediaItem.mediaId == it.mediaId } }
        }
    }

    val shouldLoadSuggestions by remember {
        derivedStateOf {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }
    }

    LaunchedEffect(mediaItemIndex, shouldLoadSuggestions) {
        if (shouldLoadSuggestions) withContext(Dispatchers.IO) {
            suggestions = runCatching {
                Innertube.nextPage(
                    NextBody(videoId = windows[mediaItemIndex].mediaItem.mediaId)
                )?.mapCatching { page ->
                    page.itemsPage?.items?.map { it.asMediaItem }
                }
            }.also { it.exceptionOrNull()?.printStackTrace() }.getOrNull()
        }
    }

    LaunchedEffect(mediaItemIndex) {
        suggestions = null
    }

    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItemIndex =
                    if (binder.player.mediaItemCount == 0) -1 else binder.player.currentMediaItemIndex
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                windows = timeline.windows
                mediaItemIndex =
                    if (binder.player.mediaItemCount == 0) -1 else binder.player.currentMediaItemIndex
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = binder.player.shouldBePlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                shouldBePlaying = binder.player.shouldBePlaying
            }
        }
    }

    BottomSheet(
        state = layoutState,
        modifier = modifier.fillMaxSize(),
        collapsedContent = { innerModifier ->
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(colorPalette.background2)
                    .fillMaxSize()
                    .then(innerModifier)
                    .padding(horizontalBottomPaddingValues),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                beforeContent()
                Spacer(modifier = Modifier.weight(1f))

                Image(
                    painter = painterResource(R.drawable.playlist),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.text),
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.weight(1f))
                afterContent()
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    ) {
        val musicBarsTransition = updateTransition(
            targetState = if (reorderingState.isDragging) -1L else mediaItemIndex,
            label = ""
        )

        LaunchedEffect(Unit) {
            lazyListState.scrollToItem(mediaItemIndex.coerceAtLeast(0))
        }

        Column {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(colorPalette.background1)
                    .weight(1f)
            ) {
                LookaheadScope {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = windowInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .asPaddingValues(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.nestedScroll(scrollConnection)
                    ) {
                        itemsIndexed(
                            items = windows,
                            key = { _, window -> window.uid.hashCode() },
                            contentType = { _, _ -> ContentType.Window }
                        ) { i, window ->
                            val isPlayingThisMediaItem = mediaItemIndex == window.firstPeriodIndex

                            SongItem(
                                song = window.mediaItem,
                                thumbnailSize = Dimensions.thumbnails.song,
                                onThumbnailContent = {
                                    musicBarsTransition.AnimatedVisibility(
                                        visible = { it == window.firstPeriodIndex },
                                        enter = fadeIn(tween(800)),
                                        exit = fadeOut(tween(800))
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.25f),
                                                    shape = thumbnailShape
                                                )
                                                .size(Dimensions.thumbnails.song)
                                        ) {
                                            if (shouldBePlaying) MusicBars(
                                                color = colorPalette.onOverlay,
                                                modifier = Modifier.height(24.dp)
                                            ) else Image(
                                                painter = painterResource(R.drawable.play),
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                },
                                trailingContent = {
                                    ReorderHandle(
                                        reorderingState = reorderingState,
                                        index = i
                                    )
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                QueuedMediaItemMenu(
                                                    mediaItem = window.mediaItem,
                                                    indexInQueue = if (isPlayingThisMediaItem) null
                                                    else window.firstPeriodIndex,
                                                    onDismiss = menuState::hide
                                                )
                                            }
                                        },
                                        onClick = {
                                            if (isPlayingThisMediaItem) {
                                                if (shouldBePlaying) binder.player.pause() else binder.player.play()
                                            } else {
                                                binder.player.seekToDefaultPosition(window.firstPeriodIndex)
                                                binder.player.playWhenReady = true
                                            }
                                        }
                                    )
                                    .animateItemPlacement(reorderingState)
                                    .draggedItem(
                                        reorderingState = reorderingState,
                                        index = i
                                    )
                                    .background(colorPalette.background1)
                                    .let {
                                        if (PlayerPreferences.horizontalSwipeToRemoveItem && !isPlayingThisMediaItem)
                                            it.swipeToClose(
                                                key = windows,
                                                delay = 100.milliseconds,
                                                requireUnconsumed = true
                                            ) {
                                                binder.player.removeMediaItem(window.firstPeriodIndex)
                                            }
                                        else it
                                    },
                                clip = !reorderingState.isDragging,
                                hideExplicit = !isPlayingThisMediaItem && AppearancePreferences.hideExplicit
                            )
                        }

                        item(
                            key = "divider",
                            contentType = { ContentType.Divider }
                        ) {
                            if (visibleSuggestions.isNotEmpty()) HorizontalDivider(
                                modifier = Modifier.padding(start = 28.dp + Dimensions.thumbnails.song)
                            )
                        }

                        items(
                            items = visibleSuggestions,
                            key = { "suggestion_${it.mediaId}" },
                            contentType = { ContentType.Suggestion }
                        ) {
                            SongItem(
                                song = it,
                                thumbnailSize = Dimensions.thumbnails.song,
                                modifier = Modifier.clickable {
                                    menuState.display {
                                        BaseMediaItemMenu(
                                            onDismiss = { menuState.hide() },
                                            mediaItem = it,
                                            onEnqueue = { binder.player.enqueue(it) },
                                            onPlayNext = { binder.player.addNext(it) }
                                        )
                                    }
                                },
                                trailingContent = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(
                                            space = 12.dp,
                                            alignment = Alignment.End
                                        )
                                    ) {
                                        IconButton(
                                            icon = R.drawable.play_skip_forward,
                                            color = colorPalette.text,
                                            onClick = {
                                                binder.player.addNext(it)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        IconButton(
                                            icon = R.drawable.enqueue,
                                            color = colorPalette.text,
                                            onClick = {
                                                binder.player.enqueue(it)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                        }

                        item(
                            key = "loading",
                            contentType = { ContentType.Placeholder }
                        ) {
                            if (binder.isLoadingRadio || suggestions == null)
                                Column(modifier = Modifier.shimmer()) {
                                    repeat(3) { index ->
                                        SongItemPlaceholder(
                                            thumbnailSize = Dimensions.thumbnails.song,
                                            modifier = Modifier
                                                .alpha(1f - index * 0.125f)
                                                .fillMaxWidth()
                                        )
                                    }
                                }
                        }
                    }
                }

                FloatingActionsContainerWithScrollToTop(
                    lazyListState = lazyListState,
                    icon = R.drawable.shuffle,
                    visible = !reorderingState.isDragging,
                    insets = windowInsets.only(WindowInsetsSides.Horizontal),
                    onClick = {
                        reorderingState.coroutineScope.launch {
                            lazyListState.smoothScrollToTop()
                        }.invokeOnCompletion {
                            binder.player.shuffleQueue()
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .clickable(onClick = layoutState::collapseSoft)
                    .background(colorPalette.background2)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(horizontalBottomPaddingValues)
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextToggle(
                    state = PlayerPreferences.queueLoopEnabled,
                    toggleState = {
                        PlayerPreferences.queueLoopEnabled = !PlayerPreferences.queueLoopEnabled
                    },
                    name = stringResource(R.string.queue_loop)
                )

                Spacer(modifier = Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.chevron_down),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.text),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.weight(1f))

                BasicText(
                    text = pluralStringResource(
                        id = R.plurals.song_count_plural,
                        count = windows.size,
                        windows.size
                    ),
                    style = typography.xxs.medium,
                    modifier = Modifier
                        .clip(16.dp.roundedShape)
                        .clickable {
                            fun addToPlaylist(playlist: Playlist, index: Int) = transaction {
                                val playlistId = Database
                                    .insert(playlist)
                                    .takeIf { it != -1L } ?: playlist.id

                                windows.forEachIndexed { i, window ->
                                    val mediaItem = window.mediaItem

                                    Database.insert(mediaItem)
                                    Database.insert(
                                        SongPlaylistMap(
                                            songId = mediaItem.mediaId,
                                            playlistId = playlistId,
                                            position = index + i
                                        )
                                    )
                                }
                            }

                            menuState.display {
                                var isCreatingNewPlaylist by rememberSaveable { mutableStateOf(false) }

                                val playlistPreviews by remember {
                                    Database
                                        .playlistPreviews(
                                            sortBy = PlaylistSortBy.DateAdded,
                                            sortOrder = SortOrder.Descending
                                        )
                                        .onFirst { isCreatingNewPlaylist = it.isEmpty() }
                                }.collectAsState(initial = null, context = Dispatchers.IO)

                                if (isCreatingNewPlaylist) TextFieldDialog(
                                    hintText = stringResource(R.string.enter_playlist_name_prompt),
                                    onDismiss = { isCreatingNewPlaylist = false },
                                    onAccept = { text ->
                                        menuState.hide()
                                        addToPlaylist(Playlist(name = text), 0)
                                    }
                                )

                                Menu {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(horizontal = 24.dp, vertical = 8.dp)
                                            .fillMaxWidth()
                                    ) {
                                        BasicText(
                                            text = stringResource(R.string.add_queue_to_playlist),
                                            style = typography.m.semiBold,
                                            overflow = TextOverflow.Ellipsis,
                                            maxLines = 2,
                                            modifier = Modifier.weight(weight = 2f, fill = false)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        SecondaryTextButton(
                                            text = stringResource(R.string.new_playlist),
                                            onClick = { isCreatingNewPlaylist = true },
                                            alternative = true,
                                            modifier = Modifier.weight(weight = 1f, fill = false)
                                        )
                                    }

                                    if (playlistPreviews?.isEmpty() == true)
                                        Spacer(modifier = Modifier.height(160.dp))

                                    playlistPreviews?.forEach { playlistPreview ->
                                        MenuEntry(
                                            icon = R.drawable.playlist,
                                            text = playlistPreview.playlist.name,
                                            secondaryText = pluralStringResource(
                                                id = R.plurals.song_count_plural,
                                                count = playlistPreview.songCount,
                                                playlistPreview.songCount
                                            ),
                                            onClick = {
                                                menuState.hide()
                                                addToPlaylist(
                                                    playlistPreview.playlist,
                                                    playlistPreview.songCount
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        .background(colorPalette.background1)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@JvmInline
private value class ContentType private constructor(val value: Int) {
    companion object {
        val Window = ContentType(value = 0)
        val Divider = ContentType(value = 1)
        val Suggestion = ContentType(value = 2)
        val Placeholder = ContentType(value = 3)
    }
}

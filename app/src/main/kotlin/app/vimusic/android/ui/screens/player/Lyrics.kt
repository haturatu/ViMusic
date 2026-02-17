package app.vimusic.android.ui.screens.player

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import app.vimusic.android.LocalAppContainer
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.models.Lyrics
import app.vimusic.android.preferences.PlayerPreferences
import app.vimusic.android.service.LOCAL_KEY_PREFIX
import app.vimusic.android.ui.components.LocalMenuState
import app.vimusic.android.ui.components.themed.CircularProgressIndicator
import app.vimusic.android.ui.components.themed.DefaultDialog
import app.vimusic.android.ui.components.themed.Menu
import app.vimusic.android.ui.components.themed.MenuEntry
import app.vimusic.android.ui.components.themed.TextField
import app.vimusic.android.ui.components.themed.TextFieldDialog
import app.vimusic.android.ui.components.themed.TextPlaceholder
import app.vimusic.android.ui.components.themed.ValueSelectorDialogBody
import app.vimusic.android.ui.modifiers.verticalFadingEdge
import app.vimusic.android.ui.viewmodels.PlayerLyricsViewModel
import app.vimusic.android.utils.SynchronizedLyrics
import app.vimusic.android.utils.SynchronizedLyricsState
import app.vimusic.android.utils.center
import app.vimusic.android.utils.color
import app.vimusic.android.utils.isInPip
import app.vimusic.android.utils.medium
import app.vimusic.android.utils.semiBold
import app.vimusic.android.utils.toast
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.onOverlay
import app.vimusic.core.ui.onOverlayShimmer
import app.vimusic.core.ui.overlay
import app.vimusic.core.ui.utils.dp
import app.vimusic.providers.lrclib.LrcParser
import app.vimusic.providers.lrclib.models.Track
import app.vimusic.providers.lrclib.toLrcFile
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val UPDATE_DELAY = 50L

@Composable
fun Lyrics(
    mediaId: String,
    isDisplayed: Boolean,
    onDismiss: () -> Unit,
    mediaMetadataProvider: () -> MediaMetadata,
    durationProvider: () -> Long,
    ensureSongInserted: () -> Unit,
    modifier: Modifier = Modifier,
    onMenuLaunch: () -> Unit = { },
    onOpenDialog: (() -> Unit)? = null,
    shouldShowSynchronizedLyrics: Boolean = PlayerPreferences.isShowingSynchronizedLyrics,
    setShouldShowSynchronizedLyrics: (Boolean) -> Unit = {
        PlayerPreferences.isShowingSynchronizedLyrics = it
    },
    shouldKeepScreenAwake: Boolean = PlayerPreferences.lyricsKeepScreenAwake,
    shouldUpdateLyrics: Boolean = true,
    showControls: Boolean = true
) = AnimatedVisibility(
    visible = isDisplayed,
    enter = fadeIn(),
    exit = fadeOut()
) {
    val viewModel: PlayerLyricsViewModel = viewModel(
        key = "player_lyrics",
        factory = PlayerLyricsViewModel.factory(LocalAppContainer.current.playerLyricsRepository)
    )
    val currentEnsureSongInserted by rememberUpdatedState(ensureSongInserted)
    val currentMediaMetadataProvider by rememberUpdatedState(mediaMetadataProvider)
    val currentDurationProvider by rememberUpdatedState(durationProvider)

    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val noBrowserInstalled = stringResource(R.string.no_browser_installed)
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val density = LocalDensity.current
    val view = LocalView.current

    val pip = isInPip()

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }

    val showSynchronizedLyrics = shouldShowSynchronizedLyrics

    var editing by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var picking by remember(mediaId, shouldShowSynchronizedLyrics) { mutableStateOf(false) }
    var isFetchingFixed by remember(mediaId) { mutableStateOf(false) }
    var hasFixedFetchFinished by remember(mediaId) { mutableStateOf(false) }
    var isFetchingSynced by remember(mediaId) { mutableStateOf(false) }
    var hasSyncedFetchFinished by remember(mediaId) { mutableStateOf(false) }

    val displaySyncedLyrics = remember(lyrics, showSynchronizedLyrics) {
        showSynchronizedLyrics && !lyrics?.synced.isNullOrBlank()
    }
    val text = remember(lyrics, displaySyncedLyrics) {
        if (displaySyncedLyrics) lyrics?.synced else lyrics?.fixed
    }
    val showLoading = remember(
        shouldUpdateLyrics,
        showSynchronizedLyrics,
        displaySyncedLyrics,
        hasFixedFetchFinished,
        isFetchingFixed,
        isFetchingSynced,
        hasSyncedFetchFinished,
        lyrics
    ) {
        if (showSynchronizedLyrics) {
            !displaySyncedLyrics && shouldUpdateLyrics &&
                    (isFetchingSynced || !hasSyncedFetchFinished)
        } else {
            lyrics?.fixed.isNullOrBlank() && shouldUpdateLyrics &&
                    (isFetchingFixed || !hasFixedFetchFinished)
        }
    }
    val showError = remember(showSynchronizedLyrics, showLoading, lyrics) {
        if (showSynchronizedLyrics) !showLoading && lyrics?.synced.isNullOrBlank()
        else !showLoading && lyrics?.fixed.isNullOrBlank()
    }
    val showLoadingOverlay = remember(showLoading, text) {
        showLoading && text.isNullOrBlank()
    }
    val showSyncedInlineLoading = remember(
        showSynchronizedLyrics,
        displaySyncedLyrics,
        isFetchingSynced,
        showLoadingOverlay
    ) {
        showSynchronizedLyrics && !displaySyncedLyrics && isFetchingSynced && !showLoadingOverlay
    }
    var invalidLrc by remember(text) { mutableStateOf(false) }

    DisposableEffect(shouldKeepScreenAwake) {
        view.keepScreenOn = shouldKeepScreenAwake

        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(mediaId) {
        runCatching {
            withContext(Dispatchers.IO) {
                viewModel
                    .observeLyrics(mediaId)
                    .distinctUntilChanged()
                    .cancellable()
                    .collect { currentLyrics ->
                        lyrics = currentLyrics
                        if (!currentLyrics?.fixed.isNullOrBlank()) hasFixedFetchFinished = true
                        if (!currentLyrics?.synced.isNullOrBlank()) hasSyncedFetchFinished = true

                        if (
                            shouldUpdateLyrics &&
                            currentLyrics?.fixed.isNullOrBlank() &&
                            !isFetchingFixed &&
                            !hasFixedFetchFinished
                        ) {
                            val mediaMetadata = currentMediaMetadataProvider()
                            var duration =
                                withContext(Dispatchers.Main) { currentDurationProvider() }

                            while (duration == C.TIME_UNSET) {
                                delay(100)
                                duration =
                                    withContext(Dispatchers.Main) { currentDurationProvider() }
                            }

                            val album = mediaMetadata.albumTitle?.toString()
                            val artist = mediaMetadata.artist?.toString().orEmpty()
                            val title = mediaMetadata.title?.toString().orEmpty().let {
                                if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                                    .substringBeforeLast('.')
                                    .trim()
                                else it
                            }

                            val normalizedTitle = title.split("(")[0].trim()

                            isFetchingFixed = true
                            try {
                                var fixed = currentLyrics?.fixed
                                if (fixed.isNullOrBlank()) {
                                    fixed = viewModel.fetchInnertubeLyrics(mediaId)
                                }

                                var attempt = 0
                                while (attempt < 3 && fixed.isNullOrBlank()) {
                                    if (fixed.isNullOrBlank()) {
                                        fixed = viewModel.fetchBestLrcLibLyrics(
                                            artist = artist,
                                            title = title,
                                            duration = duration.milliseconds,
                                            album = album,
                                            synced = false
                                        )
                                    }

                                    if (fixed.isNullOrBlank()) {
                                        fixed = viewModel.fetchBestLrcLibLyrics(
                                            artist = artist,
                                            title = normalizedTitle,
                                            duration = duration.milliseconds,
                                            album = album,
                                            synced = false
                                        )
                                    }
                                    attempt++
                                }

                                Lyrics(
                                    songId = mediaId,
                                    fixed = fixed.orEmpty(),
                                    synced = currentLyrics?.synced.orEmpty()
                                ).also {
                                    ensureActive()
                                    runCatching {
                                        currentEnsureSongInserted()
                                        viewModel.upsertLyrics(it)
                                    }
                                }
                            } finally {
                                isFetchingFixed = false
                                hasFixedFetchFinished = true
                            }
                        }
                    }
            }
        }.exceptionOrNull()?.let {
            if (it is CancellationException) throw it
            else it.printStackTrace()
        }
    }

    LaunchedEffect(mediaId, shouldShowSynchronizedLyrics, lyrics?.synced, shouldUpdateLyrics) {
        if (!shouldShowSynchronizedLyrics) {
            hasSyncedFetchFinished = false
        }
        if (!shouldUpdateLyrics) {
            hasSyncedFetchFinished = true
            return@LaunchedEffect
        }
        if (!shouldShowSynchronizedLyrics) return@LaunchedEffect
        if (!lyrics?.synced.isNullOrBlank() || isFetchingSynced) return@LaunchedEffect

        isFetchingSynced = true
        hasSyncedFetchFinished = false
        try {
            runCatching {
                withContext(Dispatchers.IO) {
                    val mediaMetadata = currentMediaMetadataProvider()
                    var duration = withContext(Dispatchers.Main) { currentDurationProvider() }

                    while (duration == C.TIME_UNSET) {
                        delay(100)
                        duration = withContext(Dispatchers.Main) { currentDurationProvider() }
                    }

                    val album = mediaMetadata.albumTitle?.toString()
                    val artist = mediaMetadata.artist?.toString().orEmpty()
                    val title = mediaMetadata.title?.toString().orEmpty().let {
                        if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                            .substringBeforeLast('.')
                            .trim()
                        else it
                    }
                    val normalizedTitle = title.split("(")[0].trim()

                    var synced = lyrics?.synced
                    var attempt = 0
                    while (attempt < 3) {
                        if (synced.isNullOrBlank()) {
                            synced = viewModel.fetchBestLrcLibLyrics(
                                artist = artist,
                                title = title,
                                duration = duration.milliseconds,
                                album = album
                            )
                        }

                        if (synced.isNullOrBlank()) {
                            synced = viewModel.fetchBestLrcLibLyrics(
                                artist = artist,
                                title = normalizedTitle,
                                duration = duration.milliseconds,
                                album = album
                            )
                        }

                        if (synced.isNullOrBlank()) {
                            synced = viewModel.fetchKuGouLyrics(
                                artist = artist,
                                title = title,
                                durationSeconds = duration / 1000
                            )
                        }

                        if (!synced.isNullOrBlank()) break
                        attempt++
                    }

                    if (!synced.isNullOrBlank()) {
                        ensureActive()
                        runCatching {
                            currentEnsureSongInserted()
                            viewModel.upsertLyrics(
                                Lyrics(
                                    songId = mediaId,
                                    fixed = lyrics?.fixed.orEmpty(),
                                    synced = synced.orEmpty()
                                )
                            )
                        }
                    }
                }
            }.exceptionOrNull()?.let {
                if (it is CancellationException) throw it
                else it.printStackTrace()
            }
        } finally {
            isFetchingSynced = false
            hasSyncedFetchFinished = true
        }
    }

    if (editing) TextFieldDialog(
        hintText = stringResource(R.string.enter_lyrics),
        initialTextInput = (if (shouldShowSynchronizedLyrics) lyrics?.synced else lyrics?.fixed).orEmpty(),
        singleLine = false,
        maxLines = 10,
        isTextInputValid = { true },
        onDismiss = { editing = false },
        onAccept = {
            runCatching {
                currentEnsureSongInserted()
                viewModel.upsertLyrics(
                    if (shouldShowSynchronizedLyrics) Lyrics(
                        songId = mediaId,
                        fixed = lyrics?.fixed,
                        synced = it
                    ) else Lyrics(
                        songId = mediaId,
                        fixed = it,
                        synced = lyrics?.synced
                    )
                )
            }
        }
    )

    if (picking && shouldShowSynchronizedLyrics) {
        var query by rememberSaveable {
            mutableStateOf(
                currentMediaMetadataProvider().title?.toString().orEmpty().let {
                    if (mediaId.startsWith(LOCAL_KEY_PREFIX)) it
                        .substringBeforeLast('.')
                        .trim()
                    else it
                }
            )
        }

        LrcLibSearchDialog(
            query = query,
            setQuery = { query = it },
            onDismiss = { picking = false },
            onPick = {
                runCatching {
                    viewModel.upsertLyrics(
                        Lyrics(
                            songId = mediaId,
                            fixed = lyrics?.fixed,
                            synced = it.syncedLyrics
                        )
                    )
                }
            }
        )
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .fillMaxSize()
            .background(colorPalette.overlay)
    ) {
        val animatedHeight by animateDpAsState(
            targetValue = maxHeight,
            label = ""
        )

        AnimatedVisibility(
            visible = showError,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(
                    if (shouldShowSynchronizedLyrics) R.string.synchronized_lyrics_not_available
                    else R.string.lyrics_not_available
                ),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = !text.isNullOrBlank() && !showError && invalidLrc && displaySyncedLyrics,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            BasicText(
                text = stringResource(R.string.invalid_synchronized_lyrics),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(all = 8.dp)
                    .fillMaxWidth(),
                maxLines = if (pip) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = showSyncedInlineLoading && !showError,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .background(Color.Black.copy(0.4f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .size(20.dp)
            )
        }

        val lyricsState = rememberSaveable(text) {
            val file = lyrics?.synced?.takeIf { it.isNotBlank() }?.let {
                LrcParser.parse(it)?.toLrcFile()
            }

            SynchronizedLyricsState(
                sentences = file?.lines,
                offset = file?.offset?.inWholeMilliseconds ?: 0L
            )
        }

        val synchronizedLyrics = remember(lyricsState) {
            invalidLrc = lyricsState.sentences == null
            lyricsState.sentences?.let {
                SynchronizedLyrics(it.toImmutableMap()) {
                    binder?.player?.let { player ->
                        player.currentPosition + UPDATE_DELAY + lyricsState.offset -
                                (lyrics?.startTime ?: 0L)
                    } ?: 0L
                }
            }
        }

        AnimatedContent(
            targetState = displaySyncedLyrics,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) { synchronized ->
            val lazyListState = rememberLazyListState()
            if (synchronized) {
                LaunchedEffect(synchronizedLyrics, density, animatedHeight) {
                    val currentSynchronizedLyrics = synchronizedLyrics ?: return@LaunchedEffect
                    val centerOffset = with(density) { (-animatedHeight / 3).roundToPx() }

                    lazyListState.animateScrollToItem(
                        index = currentSynchronizedLyrics.index + 1,
                        scrollOffset = centerOffset
                    )

                    while (true) {
                        delay(UPDATE_DELAY)
                        if (!currentSynchronizedLyrics.update()) continue

                        lazyListState.animateScrollToItem(
                            index = currentSynchronizedLyrics.index + 1,
                            scrollOffset = centerOffset
                        )
                    }
                }

                if (synchronizedLyrics != null) LazyColumn(
                    state = lazyListState,
                    userScrollEnabled = false,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .verticalFadingEdge()
                        .fillMaxWidth()
                ) {
                    item(key = "header", contentType = 0) {
                        Spacer(modifier = Modifier.height(maxHeight))
                    }
                    itemsIndexed(
                        items = synchronizedLyrics.sentences.values.toImmutableList()
                    ) { index, sentence ->
                        val color by animateColorAsState(
                            if (index == synchronizedLyrics.index) Color.White
                            else colorPalette.textDisabled
                        )

                        if (sentence.isBlank()) Image(
                            painter = painterResource(R.drawable.musical_notes),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(color),
                            modifier = Modifier
                                .padding(vertical = 4.dp, horizontal = 32.dp)
                                .size(typography.xs.fontSize.dp)
                        ) else BasicText(
                            text = sentence,
                            style = typography.xs.center.medium.color(color),
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 32.dp)
                        )
                    }
                    item(key = "footer", contentType = 0) {
                        Spacer(modifier = Modifier.height(maxHeight))
                    }
                }
            } else BasicText(
                text = lyrics?.fixed.orEmpty(),
                style = typography.xs.center.medium.color(colorPalette.onOverlay),
                modifier = Modifier
                    .verticalFadingEdge()
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(vertical = maxHeight / 4, horizontal = 32.dp)
            )
        }

        if (showLoadingOverlay && !showError) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.shimmer()
        ) {
            repeat(4) {
                TextPlaceholder(
                    color = colorPalette.onOverlayShimmer,
                    modifier = Modifier.alpha(1f - it * 0.2f)
                )
            }
        }

        if (showControls) {
            if (onOpenDialog != null) Image(
                painter = painterResource(R.drawable.expand),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                modifier = Modifier
                    .padding(all = 4.dp)
                    .clickable(
                        indication = ripple(bounded = false),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onOpenDialog()
                        }
                    )
                    .padding(all = 8.dp)
                    .size(20.dp)
                    .align(Alignment.BottomStart)
            )

            Image(
                painter = painterResource(R.drawable.ellipsis_horizontal),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.onOverlay),
                modifier = Modifier
                    .padding(all = 4.dp)
                    .clickable(
                        indication = ripple(bounded = false),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            onMenuLaunch()
                            menuState.display {
                                Menu {
                                    MenuEntry(
                                        icon = R.drawable.time,
                                        text = stringResource(
                                            if (shouldShowSynchronizedLyrics) R.string.show_unsynchronized_lyrics
                                            else R.string.show_synchronized_lyrics
                                        ),
                                        secondaryText = if (shouldShowSynchronizedLyrics) null
                                        else stringResource(R.string.provided_lyrics_by),
                                        onClick = {
                                            menuState.hide()
                                            setShouldShowSynchronizedLyrics(!shouldShowSynchronizedLyrics)
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.pencil,
                                        text = stringResource(R.string.edit_lyrics),
                                        onClick = {
                                            menuState.hide()
                                            editing = true
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.search,
                                        text = stringResource(R.string.search_lyrics_online),
                                        onClick = {
                                            menuState.hide()
                                            val mediaMetadata = currentMediaMetadataProvider()

                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                                                        putExtra(
                                                            SearchManager.QUERY,
                                                            "${mediaMetadata.title} ${mediaMetadata.artist} lyrics"
                                                        )
                                                    }
                                                )
                                            } catch (e: ActivityNotFoundException) {
                                                context.toast(noBrowserInstalled)
                                            }
                                        }
                                    )

                                    MenuEntry(
                                        icon = R.drawable.sync,
                                        text = stringResource(R.string.refetch_lyrics),
                                        enabled = lyrics != null,
                                        onClick = {
                                            menuState.hide()
                                            runCatching {
                                                currentEnsureSongInserted()
                                                viewModel.upsertLyrics(
                                                    if (shouldShowSynchronizedLyrics) Lyrics(
                                                        songId = mediaId,
                                                        fixed = lyrics?.fixed,
                                                        synced = null
                                                    ) else Lyrics(
                                                        songId = mediaId,
                                                        fixed = null,
                                                        synced = lyrics?.synced
                                                    )
                                                )
                                            }
                                        }
                                    )

                                    if (shouldShowSynchronizedLyrics) {
                                        MenuEntry(
                                            icon = R.drawable.download,
                                            text = stringResource(R.string.pick_from_lrclib),
                                            onClick = {
                                                menuState.hide()
                                                picking = true
                                            }
                                        )
                                        MenuEntry(
                                            icon = R.drawable.play_skip_forward,
                                            text = stringResource(R.string.set_lyrics_start_offset),
                                            secondaryText = stringResource(
                                                R.string.set_lyrics_start_offset_description
                                            ),
                                            onClick = {
                                                menuState.hide()
                                                lyrics?.let {
                                                    val startTime = binder?.player?.currentPosition
                                                    viewModel.upsertLyrics(it.copy(startTime = startTime))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                    .padding(all = 8.dp)
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun LrcLibSearchDialog(
    query: String,
    setQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Track) -> Unit,
    modifier: Modifier = Modifier
) = DefaultDialog(
    onDismiss = onDismiss,
    horizontalPadding = 0.dp,
    modifier = modifier
) {
    val (_, typography) = LocalAppearance.current

    val tracks = remember { mutableStateListOf<Track>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    val viewModel: PlayerLyricsViewModel = viewModel(
        key = "player_lyrics",
        factory = PlayerLyricsViewModel.factory(LocalAppContainer.current.playerLyricsRepository)
    )

    LaunchedEffect(query) {
        loading = true
        error = false

        delay(1000)

        viewModel.searchSyncedLrcLib(query)?.onSuccess { newTracks ->
            tracks.clear()
            tracks.addAll(newTracks.filter { !it.syncedLyrics.isNullOrBlank() })
            loading = false
            error = false
        }?.onFailure {
            loading = false
            error = true
            it.printStackTrace()
        } ?: run { loading = false }
    }

    TextField(
        value = query,
        onValueChange = setQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        maxLines = 1,
        singleLine = true
    )
    Spacer(modifier = Modifier.height(8.dp))

    when {
        loading -> CircularProgressIndicator(
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        error || tracks.isEmpty() -> BasicText(
            text = stringResource(R.string.no_lyrics_found),
            style = typography.s.semiBold.center,
            modifier = Modifier
                .padding(all = 24.dp)
                .align(Alignment.CenterHorizontally)
        )

        else -> ValueSelectorDialogBody(
            onDismiss = onDismiss,
            title = stringResource(R.string.choose_lyric_track),
            selectedValue = null,
            values = tracks.toImmutableList(),
            onValueSelect = {
                onPick(it)
                onDismiss()
            },
            valueText = {
                "${it.artistName} - ${it.trackName} (${
                    it.duration.seconds.toComponents { minutes, seconds, _ ->
                        "$minutes:${seconds.toString().padStart(2, '0')}"
                    }
                })"
            }
        )
    }
}

package app.vimusic.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Left
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Right
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import app.vimusic.android.Database
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.preferences.PlayerPreferences
import app.vimusic.android.service.LoginRequiredException
import app.vimusic.android.service.PlayableFormatNotFoundException
import app.vimusic.android.service.RestrictedVideoException
import app.vimusic.android.service.UnplayableException
import app.vimusic.android.service.VideoIdMismatchException
import app.vimusic.android.service.isLocal
import app.vimusic.android.ui.modifiers.onSwipe
import app.vimusic.android.utils.forceSeekToNext
import app.vimusic.android.utils.forceSeekToPrevious
import app.vimusic.android.utils.thumbnail
import app.vimusic.android.utils.windowState
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.utils.px
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

@Composable
fun Thumbnail(
    isShowingLyrics: Boolean,
    onShowLyrics: (Boolean) -> Unit,
    isShowingStatsForNerds: Boolean,
    onShowStatsForNerds: (Boolean) -> Unit,
    onOpenDialog: () -> Unit,
    likedAt: Long?,
    setLikedAt: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
    shouldShowSynchronizedLyrics: Boolean = PlayerPreferences.isShowingSynchronizedLyrics,
    setShouldShowSynchronizedLyrics: (Boolean) -> Unit = {
        PlayerPreferences.isShowingSynchronizedLyrics = it
    },
    showLyricsControls: Boolean = true
) {
    val binder = LocalPlayerServiceBinder.current
    val (colorPalette, _, _, thumbnailShape) = LocalAppearance.current

    val (window, error) = windowState()

    val coroutineScope = rememberCoroutineScope()
    val transitionState = remember { SeekableTransitionState(false) }
    val transition = rememberTransition(transitionState)
    val opacity by transition.animateFloat(label = "") { if (it) 1f else 0f }
    val scale by transition.animateFloat(
        label = "",
        transitionSpec = {
            spring(dampingRatio = Spring.DampingRatioLowBouncy)
        }
    ) { if (it) 1f else 0f }

    AnimatedContent(
        targetState = window,
        transitionSpec = {
            val duration = 500
            val initial = initialState
            val target = targetState

            if (initial == null || target == null) return@AnimatedContent ContentTransform(
                targetContentEnter = fadeIn(tween(duration)),
                initialContentExit = fadeOut(tween(duration)),
                sizeTransform = null
            )

            val sizeTransform = SizeTransform(clip = false) { _, _ ->
                tween(durationMillis = duration, delayMillis = duration)
            }

            val direction = if (target.firstPeriodIndex < initial.firstPeriodIndex) Right else Left

            ContentTransform(
                targetContentEnter = slideIntoContainer(direction, tween(duration)) +
                        fadeIn(tween(duration)) +
                        scaleIn(tween(duration), 0.85f),
                initialContentExit = slideOutOfContainer(direction, tween(duration)) +
                        fadeOut(tween(duration)) +
                        scaleOut(tween(duration), 0.85f),
                sizeTransform = sizeTransform
            )
        },
        modifier = modifier.onSwipe(
            onSwipeLeft = {
                binder?.player?.forceSeekToNext()
            },
            onSwipeRight = {
                binder?.player?.forceSeekToPrevious(seekToStart = false)
            }
        ),
        contentAlignment = Alignment.Center,
        label = ""
    ) { currentWindow ->
        val shadowElevation by animateDpAsState(
            targetValue = if (window == currentWindow) 8.dp else 0.dp,
            animationSpec = tween(
                durationMillis = 500,
                easing = LinearEasing
            ),
            label = ""
        )
        val blurRadius by animateDpAsState(
            targetValue = if (isShowingLyrics || error != null || isShowingStatsForNerds) 8.dp else 0.dp,
            animationSpec = tween(500),
            label = ""
        )

        if (currentWindow != null) Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = shadowElevation,
                    shape = thumbnailShape,
                    clip = false
                )
                .clip(thumbnailShape)
        ) {
            var height by remember { mutableIntStateOf(0) }

            AsyncImage(
                model = currentWindow.mediaItem.mediaMetadata.artworkUri
                    ?.thumbnail((Dimensions.thumbnails.player.song - 64.dp).px),
                error = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onShowLyrics(true) },
                            onLongPress = { onShowStatsForNerds(true) },
                            onDoubleTap = {
                                if (likedAt == null) setLikedAt(System.currentTimeMillis())

                                coroutineScope.launch {
                                    val spec = tween<Float>(durationMillis = 500)
                                    transitionState.animateTo(true, spec)
                                    transitionState.animateTo(false, spec)
                                }
                            }
                        )
                    }
                    .fillMaxWidth()
                    .animateContentSize()
                    .background(colorPalette.background0)
                    .let {
                        if (blurRadius == 0.dp) it else it.blur(radius = blurRadius)
                    }
                    .onGloballyPositioned {
                        height = it.size.height
                    }
            )

            Lyrics(
                mediaId = currentWindow.mediaItem.mediaId,
                isDisplayed = isShowingLyrics && error == null,
                onDismiss = { onShowLyrics(false) },
                ensureSongInserted = { Database.insert(currentWindow.mediaItem) },
                mediaMetadataProvider = currentWindow.mediaItem::mediaMetadata,
                durationProvider = { binder?.player?.duration ?: C.TIME_UNSET },
                onOpenDialog = onOpenDialog,
                modifier = Modifier.height(height.px.dp),
                shouldShowSynchronizedLyrics = shouldShowSynchronizedLyrics,
                setShouldShowSynchronizedLyrics = setShouldShowSynchronizedLyrics,
                showControls = showLyricsControls
            )

            StatsForNerds(
                mediaId = currentWindow.mediaItem.mediaId,
                isDisplayed = isShowingStatsForNerds && error == null,
                onDismiss = { onShowStatsForNerds(false) },
                modifier = Modifier.height(height.px.dp)
            )

            Image(
                painter = painterResource(R.drawable.heart),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.accent),
                modifier = Modifier
                    .fillMaxSize(0.5f)
                    .aspectRatio(1f)
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        alpha = opacity,
                        shadowElevation = 8.dp.px.toFloat()
                    )
            )

            PlaybackError(
                isDisplayed = error != null,
                messageProvider = {
                    if (currentWindow.mediaItem.isLocal) stringResource(R.string.error_local_music_deleted)
                    else when (error?.cause?.cause) {
                        is UnresolvedAddressException, is UnknownHostException ->
                            stringResource(R.string.error_network)

                        is PlayableFormatNotFoundException -> stringResource(R.string.error_unplayable)
                        is UnplayableException -> stringResource(R.string.error_source_deleted)
                        is LoginRequiredException, is RestrictedVideoException ->
                            stringResource(R.string.error_server_restrictions)

                        is VideoIdMismatchException -> stringResource(R.string.error_id_mismatch)
                        else -> stringResource(R.string.error_unknown_playback)
                    }
                },
                onDismiss = { binder?.player?.prepare() },
                modifier = Modifier.height(height.px.dp)
            )
        }
    }
}

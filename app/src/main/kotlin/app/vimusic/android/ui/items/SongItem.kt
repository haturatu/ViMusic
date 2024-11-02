package app.vimusic.android.ui.items

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.preferences.AppearancePreferences
import app.vimusic.android.ui.components.themed.TextPlaceholder
import app.vimusic.android.utils.medium
import app.vimusic.android.utils.secondary
import app.vimusic.android.utils.semiBold
import app.vimusic.android.utils.thumbnail
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.shimmer
import app.vimusic.core.ui.utils.px
import app.vimusic.core.ui.utils.songBundle
import app.vimusic.providers.innertube.Innertube
import coil3.compose.AsyncImage

@Composable
fun SongItem(
    song: Innertube.SongItem,
    thumbnailSize: Dp,
    modifier: Modifier = Modifier,
    showDuration: Boolean = true,
    clip: Boolean = true,
    hideExplicit: Boolean = AppearancePreferences.hideExplicit
) = SongItem(
    modifier = modifier,
    thumbnailUrl = song.thumbnail?.size(thumbnailSize.px),
    title = song.info?.name,
    authors = song.authors?.joinToString("") { it.name.orEmpty() },
    duration = song.durationText,
    explicit = song.explicit,
    thumbnailSize = thumbnailSize,
    showDuration = showDuration,
    clip = clip,
    hideExplicit = hideExplicit
)

@Composable
fun SongItem(
    song: MediaItem,
    thumbnailSize: Dp,
    modifier: Modifier = Modifier,
    onThumbnailContent: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showDuration: Boolean = true,
    clip: Boolean = true,
    hideExplicit: Boolean = AppearancePreferences.hideExplicit
) {
    val extras = remember(song) { song.mediaMetadata.extras?.songBundle }

    SongItem(
        modifier = modifier,
        thumbnailUrl = song.mediaMetadata.artworkUri.thumbnail(thumbnailSize.px)?.toString(),
        title = song.mediaMetadata.title?.toString(),
        authors = song.mediaMetadata.artist?.toString(),
        duration = extras?.durationText,
        explicit = extras?.explicit == true,
        thumbnailSize = thumbnailSize,
        onThumbnailContent = onThumbnailContent,
        trailingContent = trailingContent,
        showDuration = showDuration,
        clip = clip,
        hideExplicit = hideExplicit
    )
}

@Composable
fun SongItem(
    song: Song,
    thumbnailSize: Dp,
    modifier: Modifier = Modifier,
    index: Int? = null,
    onThumbnailContent: @Composable (BoxScope.() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    showDuration: Boolean = true,
    clip: Boolean = true,
    hideExplicit: Boolean = AppearancePreferences.hideExplicit
) = SongItem(
    modifier = modifier,
    index = index,
    thumbnailUrl = song.thumbnailUrl?.thumbnail(thumbnailSize.px),
    title = song.title,
    authors = song.artistsText,
    duration = song.durationText,
    explicit = song.explicit,
    thumbnailSize = thumbnailSize,
    onThumbnailContent = onThumbnailContent,
    trailingContent = trailingContent,
    showDuration = showDuration,
    clip = clip,
    hideExplicit = hideExplicit
)

@Composable
fun SongItem(
    thumbnailUrl: String?,
    title: String?,
    authors: String?,
    duration: String?,
    explicit: Boolean,
    thumbnailSize: Dp,
    modifier: Modifier = Modifier,
    index: Int? = null,
    onThumbnailContent: @Composable (BoxScope.() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    showDuration: Boolean = true,
    clip: Boolean = true,
    hideExplicit: Boolean = AppearancePreferences.hideExplicit
) {
    val (colorPalette, typography, _, thumbnailShape) = LocalAppearance.current

    SongItem(
        title = title,
        authors = authors,
        duration = duration,
        explicit = explicit,
        thumbnailSize = thumbnailSize,
        thumbnailContent = {
            Box(
                modifier = Modifier
                    .clip(thumbnailShape)
                    .background(colorPalette.background1)
                    .fillMaxSize()
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    error = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (index != null) {
                    Box(
                        modifier = Modifier
                            .background(color = Color.Black.copy(alpha = 0.75f))
                            .fillMaxSize()
                    )
                    BasicText(
                        text = "${index + 1}",
                        style = typography.xs.semiBold.copy(color = Color.White),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            onThumbnailContent?.invoke(this)
        },
        modifier = modifier,
        trailingContent = trailingContent,
        showDuration = showDuration,
        clip = clip,
        hideExplicit = hideExplicit
    )
}

@Composable
fun SongItem(
    title: String?,
    authors: String?,
    duration: String?,
    explicit: Boolean,
    thumbnailSize: Dp,
    thumbnailContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    showDuration: Boolean = true,
    clip: Boolean = true,
    hideExplicit: Boolean = AppearancePreferences.hideExplicit
) = if (!(hideExplicit && explicit)) ItemContainer(
    alternative = false,
    thumbnailSize = thumbnailSize,
    modifier = if (clip) Modifier.clip(LocalAppearance.current.thumbnailShape) then modifier
    else modifier
) {
    val (colorPalette, typography) = LocalAppearance.current

    Box(
        modifier = Modifier.size(thumbnailSize),
        content = thumbnailContent
    )

    ItemInfoContainer {
        trailingContent?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BasicText(
                    text = title.orEmpty(),
                    style = typography.xs.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                it()
            }
        } ?: BasicText(
            text = title.orEmpty(),
            style = typography.xs.semiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                authors?.let {
                    BasicText(
                        text = authors,
                        style = typography.xs.semiBold.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(
                            weight = 1f,
                            fill = false
                        )
                    )
                }

                if (explicit) Image(
                    painter = painterResource(R.drawable.explicit),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.text),
                    modifier = Modifier.size(15.dp)
                )
            }

            if (showDuration) duration?.let {
                BasicText(
                    text = duration,
                    style = typography.xxs.secondary.medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
} else Unit

@Composable
fun SongItemPlaceholder(
    thumbnailSize: Dp,
    modifier: Modifier = Modifier
) = ItemContainer(
    alternative = false,
    thumbnailSize = thumbnailSize,
    modifier = modifier
) {
    val (colorPalette, _, _, thumbnailShape) = LocalAppearance.current

    Spacer(
        modifier = Modifier
            .background(color = colorPalette.shimmer, shape = thumbnailShape)
            .size(thumbnailSize)
    )

    ItemInfoContainer {
        TextPlaceholder()
        TextPlaceholder()
    }
}

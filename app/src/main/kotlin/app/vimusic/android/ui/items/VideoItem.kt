package app.vimusic.android.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vimusic.android.ui.components.themed.TextPlaceholder
import app.vimusic.android.utils.color
import app.vimusic.android.utils.medium
import app.vimusic.android.utils.secondary
import app.vimusic.android.utils.semiBold
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.onOverlay
import app.vimusic.core.ui.overlay
import app.vimusic.core.ui.shimmer
import app.vimusic.core.ui.utils.roundedShape
import app.vimusic.providers.innertube.Innertube
import coil3.compose.AsyncImage

@Composable
fun VideoItem(
    video: Innertube.VideoItem,
    thumbnailWidth: Dp,
    thumbnailHeight: Dp,
    modifier: Modifier = Modifier
) = VideoItem(
    thumbnailUrl = video.thumbnail?.url,
    duration = video.durationText,
    title = video.info?.name,
    uploader = video.authors?.joinToString("") { it.name.orEmpty() },
    views = video.viewsText,
    thumbnailWidth = thumbnailWidth,
    thumbnailHeight = thumbnailHeight,
    modifier = modifier
)

@Composable
fun VideoItem(
    thumbnailUrl: String?,
    duration: String?,
    title: String?,
    uploader: String?,
    views: String?,
    thumbnailWidth: Dp,
    thumbnailHeight: Dp,
    modifier: Modifier = Modifier
) = ItemContainer(
    alternative = false,
    thumbnailSize = 0.dp,
    modifier = Modifier.clip(LocalAppearance.current.thumbnailShape) then modifier
) {
    val (colorPalette, typography, thumbnailShapeCorners) = LocalAppearance.current

    Box {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .clip(thumbnailShapeCorners.roundedShape)
                .size(width = thumbnailWidth, height = thumbnailHeight)
        )

        duration?.let {
            BasicText(
                text = duration,
                style = typography.xxs.medium.color(colorPalette.onOverlay),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(all = Dimensions.items.gap)
                    .background(
                        color = colorPalette.overlay,
                        shape = (thumbnailShapeCorners - Dimensions.items.gap).coerceAtLeast(0.dp).roundedShape
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .align(Alignment.BottomEnd)
            )
        }
    }

    ItemInfoContainer {
        BasicText(
            text = title.orEmpty(),
            style = typography.xs.semiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        BasicText(
            text = uploader.orEmpty(),
            style = typography.xs.semiBold.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        views?.let {
            BasicText(
                text = views,
                style = typography.xxs.medium.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun VideoItemPlaceholder(
    thumbnailWidth: Dp,
    thumbnailHeight: Dp,
    modifier: Modifier = Modifier
) = ItemContainer(
    alternative = false,
    thumbnailSize = 0.dp,
    modifier = modifier
) {
    val colorPalette = LocalAppearance.current.colorPalette
    val thumbnailShape = LocalAppearance.current.thumbnailShape

    Spacer(
        modifier = Modifier
            .background(color = colorPalette.shimmer, shape = thumbnailShape)
            .size(width = thumbnailWidth, height = thumbnailHeight)
    )

    ItemInfoContainer {
        TextPlaceholder()
        TextPlaceholder()
        TextPlaceholder(modifier = Modifier.padding(top = 8.dp))
    }
}

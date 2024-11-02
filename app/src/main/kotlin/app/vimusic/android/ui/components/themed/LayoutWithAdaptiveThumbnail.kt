package app.vimusic.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.vimusic.android.utils.thumbnail
import app.vimusic.core.ui.Dimensions
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.core.ui.shimmer
import app.vimusic.core.ui.utils.isLandscape
import app.vimusic.core.ui.utils.px
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer

@Composable
inline fun LayoutWithAdaptiveThumbnail(
    thumbnailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = if (isLandscape) Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
) {
    thumbnailContent()
    content()
} else Box(modifier = modifier) { content() }

fun adaptiveThumbnailContent(
    isLoading: Boolean,
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape? = null
): @Composable () -> Unit = {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
        val (colorPalette, _, _, thumbnailShape) = LocalAppearance.current
        val thumbnailSize =
            if (isLandscape) (maxHeight - 96.dp - Dimensions.items.collapsedPlayerHeight)
            else maxWidth

        val innerModifier = Modifier
            .clip(shape ?: thumbnailShape)
            .size(thumbnailSize)

        if (isLoading) Spacer(
            modifier = innerModifier
                .shimmer()
                .background(colorPalette.shimmer)
        ) else AsyncImage(
            model = url?.thumbnail(thumbnailSize.px),
            contentDescription = null,
            modifier = innerModifier.background(colorPalette.background1)
        )
    }
}

package app.vimusic.android.ui.components.themed

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import app.vimusic.android.R
import app.vimusic.android.utils.LocalPlaybackActions
import app.vimusic.android.utils.PlaylistDownloadIcon
import kotlinx.collections.immutable.toImmutableList

@Composable
fun RowScope.SongListActionsRow(
    mediaItems: List<MediaItem>?,
    showDownload: Boolean = true,
    onEnqueue: (() -> Unit)? = null,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    val playbackActions = LocalPlaybackActions.current
    val hasItems = !mediaItems.isNullOrEmpty()

    SecondaryTextButton(
        text = stringResource(R.string.enqueue),
        enabled = hasItems,
        onClick = {
            onEnqueue?.invoke() ?: mediaItems?.let(playbackActions::enqueue)
        }
    )

    Spacer(modifier = Modifier.weight(1f))

    leadingContent?.invoke(this)

    if (showDownload && hasItems) {
        mediaItems?.let { items ->
            PlaylistDownloadIcon(songs = items.toImmutableList())
        }
    }

    trailingContent?.invoke(this)
}

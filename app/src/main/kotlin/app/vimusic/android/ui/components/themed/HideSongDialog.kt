package app.vimusic.android.ui.components.themed

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.vimusic.android.LocalAppContainer
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.service.isLocal

@OptIn(UnstableApi::class)
@Composable
fun HideSongDialog(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onHideSong: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    val defaultOnHideSong = onHideSong ?: LocalAppContainer.current.songsRepository::deleteSong

    ConfirmationDialog(
        text = stringResource(R.string.confirm_hide_song),
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm()
            runCatching {
                if (!song.isLocal) binder?.cache?.removeResource(song.id)
                defaultOnHideSong(song)
            }
        },
        modifier = modifier
    )
}

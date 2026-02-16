package app.vimusic.android.ui.components.themed

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.vimusic.android.Database
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.models.Song
import app.vimusic.android.query
import app.vimusic.android.service.isLocal

@OptIn(UnstableApi::class)
@Composable
fun HideSongDialog(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onHideSong: (Song) -> Unit = {
        query { runCatching { Database.delete(it) } }
    },
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current

    ConfirmationDialog(
        text = stringResource(R.string.confirm_hide_song),
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm()
            runCatching {
                if (!song.isLocal) binder?.cache?.removeResource(song.id)
                onHideSong(song)
            }
        },
        modifier = modifier
    )
}

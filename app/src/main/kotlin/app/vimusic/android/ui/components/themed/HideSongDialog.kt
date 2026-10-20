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
import app.vimusic.android.utils.launchDatabaseAction

@OptIn(UnstableApi::class)
@Composable
fun HideSongDialog(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    onHideSong: (suspend (Song) -> Unit)? = null
) {
    val databaseScope = LocalAppContainer.current.databaseScope
    val binder = LocalPlayerServiceBinder.current
    val defaultOnHideSong: suspend (Song) -> Unit = onHideSong ?: LocalAppContainer.current.songsRepository::deleteSong

    ConfirmationDialog(
        text = stringResource(R.string.confirm_hide_song),
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm()
            databaseScope.launchDatabaseAction {
                if (!song.isLocal) binder?.cache?.removeResource(song.id)
                defaultOnHideSong(song)
            }
        },
        modifier = modifier
    )
}

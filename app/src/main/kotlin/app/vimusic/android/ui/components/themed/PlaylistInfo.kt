package app.vimusic.android.ui.components.themed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vimusic.android.models.Album
import app.vimusic.android.utils.semiBold
import app.vimusic.core.ui.LocalAppearance
import app.vimusic.providers.innertube.Innertube

@Composable
fun PlaylistInfo(
    description: String?,
    year: String?,
    otherInfo: String?,
    modifier: Modifier = Modifier
) {
    val (_, typography) = LocalAppearance.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        otherInfo?.let { info ->
            BasicText(
                text = info,
                style = typography.s.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }

        year?.let { year ->
            BasicText(
                text = year,
                style = typography.s.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }

        description?.let { description ->
            Attribution(text = description)
        }
    }
}

@Composable
fun PlaylistInfo(
    playlist: Innertube.PlaylistOrAlbumPage?,
    modifier: Modifier = Modifier
) = PlaylistInfo(
    description = playlist?.description,
    year = playlist?.year,
    otherInfo = playlist?.otherInfo,
    modifier = modifier
)

@Composable
fun PlaylistInfo(
    playlist: Album?,
    modifier: Modifier = Modifier
) = PlaylistInfo(
    description = playlist?.description,
    year = playlist?.year,
    otherInfo = playlist?.otherInfo,
    modifier = modifier
)

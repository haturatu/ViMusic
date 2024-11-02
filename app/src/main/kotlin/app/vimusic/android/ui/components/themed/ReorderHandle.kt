package app.vimusic.android.ui.components.themed

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vimusic.android.R
import app.vimusic.compose.reordering.ReorderingState
import app.vimusic.compose.reordering.reorder
import app.vimusic.core.ui.LocalAppearance

@Composable
fun ReorderHandle(
    reorderingState: ReorderingState,
    index: Int,
    modifier: Modifier = Modifier
) = IconButton(
    icon = R.drawable.reorder,
    color = LocalAppearance.current.colorPalette.textDisabled,
    indication = null,
    onClick = {},
    modifier = modifier
        .reorder(
            reorderingState = reorderingState,
            index = index
        )
        .size(18.dp)
)

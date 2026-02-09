package app.vimusic.compose.reordering

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

context(scope: LazyItemScope)
fun Modifier.animateItemPlacement(reorderingState: ReorderingState) = this.composed {
    if (reorderingState.draggingIndex == -1) {
        with(scope) {
            this@composed.animateItem(
                fadeInSpec = null,
                fadeOutSpec = null
            )
        }
    } else this
}

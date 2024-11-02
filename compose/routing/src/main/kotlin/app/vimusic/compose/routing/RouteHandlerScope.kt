package app.vimusic.compose.routing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
class RouteHandlerScope(
    val child: Route?,
    val args: Array<Any?>,
    val replace: (Route?) -> Unit,
    override val pop: () -> Unit,
    val root: RootRouter
) : Router by root {
    @Composable
    inline fun Content(content: @Composable () -> Unit) {
        if (child == null) content()
    }
}

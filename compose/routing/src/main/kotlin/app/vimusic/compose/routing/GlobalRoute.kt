package app.vimusic.compose.routing

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow

typealias RouteRequestDefinition = Pair<Route, Array<Any?>>

@JvmInline
value class RouteRequest private constructor(private val def: RouteRequestDefinition) {
    constructor(route: Route, args: Array<Any?>) : this(route to args)

    val route get() = def.first
    val args get() = def.second

    operator fun component1() = route
    operator fun component2() = args
}

internal val globalRouteFlow = MutableSharedFlow<RouteRequest>(extraBufferCapacity = 1)

@Composable
fun CallbackPredictiveBackHandler(
    enabled: Boolean,
    onStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) = PredictiveBackHandler(enabled = enabled) { progress ->
    onStart()

    // The meaning of CancellationException is different here (normally CancellationExceptions should be rethrowed)
    @Suppress("SwallowedException")
    try {
        progress.collect {
            onProgress(it.progress)
        }
        onFinish()
    } catch (e: CancellationException) {
        onCancel()
    }
}

@Composable
fun OnGlobalRoute(block: suspend (RouteRequest) -> Unit) {
    val currentBlock by rememberUpdatedState(block)

    LaunchedEffect(Unit) {
        globalRouteFlow.collect {
            currentBlock(it)
        }
    }
}

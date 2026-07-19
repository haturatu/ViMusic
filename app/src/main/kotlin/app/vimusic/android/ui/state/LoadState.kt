package app.vimusic.android.ui.state

import app.vimusic.android.utils.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data class Loading<T>(val previous: T? = null) : LoadState<T>
    data class Content<T>(val value: T) : LoadState<T>
    data class Error<T>(
        val throwable: Throwable,
        val previous: T? = null,
    ) : LoadState<T>
}

val LoadState<*>.isLoading: Boolean
    get() = this is LoadState.Loading

fun <T> LoadState<T>.contentOrNull(): T? = when (this) {
    is LoadState.Content -> value
    is LoadState.Loading -> previous
    is LoadState.Error -> previous
    LoadState.Idle -> null
}

fun <T> MutableStateFlow<LoadState<T>>.launchLoad(
    scope: CoroutineScope,
    keepPreviousOnFailure: Boolean = false,
    onSuccess: (T) -> Unit = {},
    block: suspend () -> T,
): Job {
    val previous = value.contentOrNull()
    value = LoadState.Loading(previous)

    return scope.launch {
        runSuspendCatching(block).fold(
            onSuccess = { result ->
                value = LoadState.Content(result)
                onSuccess(result)
            },
            onFailure = { error ->
                value = LoadState.Error(
                    throwable = error,
                    previous = previous.takeIf { keepPreviousOnFailure },
                )
            },
        )
    }
}

package app.vimusic.android.ui.state

import app.vimusic.android.utils.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
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
    is LoadState.Error -> previous
    LoadState.Idle,
    LoadState.Loading -> null
}

fun <T> MutableStateFlow<LoadState<T>>.launchLoad(
    scope: CoroutineScope,
    previous: T? = value.contentOrNull(),
    showPreviousWhileLoading: Boolean = false,
    keepPreviousOnFailure: Boolean = false,
    onSuccess: (T) -> Unit = {},
    block: suspend () -> T,
): Job {
    value = if (showPreviousWhileLoading && previous != null) {
        LoadState.Content(previous)
    } else {
        LoadState.Loading
    }

    return scope.launch {
        runSuspendCatching(block).fold(
            onSuccess = { result ->
                onSuccess(result)
                value = LoadState.Content(result)
            },
            onFailure = { error ->
                value = if (keepPreviousOnFailure && previous != null) {
                    LoadState.Content(previous)
                } else {
                    LoadState.Error(error, previous)
                }
            },
        )
    }
}

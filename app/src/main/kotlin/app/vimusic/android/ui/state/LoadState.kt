package app.vimusic.android.ui.state

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

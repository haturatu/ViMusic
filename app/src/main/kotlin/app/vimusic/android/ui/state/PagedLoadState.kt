package app.vimusic.android.ui.state

enum class LoadPhase {
    Idle,
    Loading,
    Error,
    Complete,
}

data class PagedLoadState<T>(
    val items: List<T> = emptyList(),
    val continuation: String? = null,
    val initialLoad: LoadPhase = LoadPhase.Idle,
    val appendLoad: LoadPhase = LoadPhase.Idle,
    val throwable: Throwable? = null,
) {
    val isInitialLoad: Boolean
        get() = initialLoad == LoadPhase.Loading

    val isLoading: Boolean
        get() = isInitialLoad || appendLoad == LoadPhase.Loading

    val hasError: Boolean
        get() = initialLoad == LoadPhase.Error || appendLoad == LoadPhase.Error

    val isComplete: Boolean
        get() = initialLoad == LoadPhase.Complete && continuation == null

    fun startLoading(): PagedLoadState<T> = if (items.isEmpty()) {
        copy(initialLoad = LoadPhase.Loading, throwable = null)
    } else {
        copy(appendLoad = LoadPhase.Loading, throwable = null)
    }

    fun append(
        newItems: List<T>,
        nextContinuation: String?,
    ): PagedLoadState<T> = copy(
        items = items + newItems,
        continuation = nextContinuation,
        initialLoad = LoadPhase.Complete,
        appendLoad = if (nextContinuation == null) LoadPhase.Complete else LoadPhase.Idle,
        throwable = null,
    )

    fun fail(error: Throwable): PagedLoadState<T> = if (items.isEmpty()) {
        copy(initialLoad = LoadPhase.Error, throwable = error)
    } else {
        copy(appendLoad = LoadPhase.Error, throwable = error)
    }

    fun retry(): PagedLoadState<T> = copy(
        initialLoad = if (initialLoad == LoadPhase.Error) LoadPhase.Idle else initialLoad,
        appendLoad = if (appendLoad == LoadPhase.Error) LoadPhase.Idle else appendLoad,
        throwable = null,
    )
}

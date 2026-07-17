package app.vimusic.android.utils

fun <T : Any> Result<T?>?.requireValue(
    nullResultMessage: String = "Request returned no result",
    nullValueMessage: String = "Response body was empty",
): Result<T> = when (this) {
    null -> Result.failure(IllegalStateException(nullResultMessage))
    else -> fold(
        onSuccess = { value ->
            value?.let(Result.Companion::success)
                ?: Result.failure(IllegalStateException(nullValueMessage))
        },
        onFailure = Result.Companion::failure,
    )
}

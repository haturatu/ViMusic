package app.vimusic.android.utils

import android.os.CancellationSignal
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException

val <T> CancellableContinuation<T>.asCancellationSignal get() = CancellationSignal().also {
    it.setOnCancelListener { cancel() }
}

@Suppress("TooGenericExceptionCaught")
suspend inline fun <T> runSuspendCatching(
    crossinline block: suspend () -> T,
): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    Result.failure(error)
}

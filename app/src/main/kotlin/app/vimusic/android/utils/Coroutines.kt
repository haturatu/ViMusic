package app.vimusic.android.utils

import android.os.CancellationSignal
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

fun CoroutineScope.launchDatabaseAction(block: suspend () -> Unit): Job = launch {
    runSuspendCatching(block).onFailure { Log.e("Database", "Database action failed", it) }
}

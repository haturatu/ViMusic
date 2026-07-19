package app.vimusic.android.utils

import dev.kathttp3.KatHttp3Exception
import dev.kathttp3.QuicTransportException
import dev.kathttp3.TlsHandshakeException
import kotlinx.coroutines.TimeoutCancellationException

/** Failures for which retrying the same request through HTTP/2 is appropriate. */
internal fun Throwable.isHttp3TransportFailure(): Boolean =
    generateSequence(this) { it.cause }
        .any { error ->
            error is TimeoutCancellationException ||
                error is KatHttp3Exception.RequestQueueTimeout ||
                error is KatHttp3Exception.Dns ||
                error is KatHttp3Exception.Timeout ||
                error is QuicTransportException ||
                error is TlsHandshakeException
        }

internal fun Throwable.hasTlsHandshakeFailure(): Boolean =
    generateSequence(this) { it.cause }.any { it is TlsHandshakeException }

/** Linux ENETUNREACH, surfaced either as errno -101 or native error text. */
internal fun Throwable.isNetworkUnreachable(): Boolean =
    generateSequence(this) { it.cause }.any { error ->
        (error is KatHttp3Exception.Native && kotlin.math.abs(error.code) == ENETUNREACH) ||
            error.message?.contains("Network is unreachable", ignoreCase = true) == true
    }

private const val ENETUNREACH = 101

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
                error is KatHttp3Exception.Dns ||
                error is KatHttp3Exception.Timeout ||
                error is QuicTransportException ||
                error is TlsHandshakeException
        }

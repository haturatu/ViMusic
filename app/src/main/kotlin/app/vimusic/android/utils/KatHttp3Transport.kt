package app.vimusic.android.utils

import app.vimusic.android.utils.AddressFamilyFallbackDnsResolver.AddressFamily
import dev.kathttp3.KatHttp3Exception
import dev.kathttp3.KatHttp3Header
import dev.kathttp3.KatHttp3Request
import dev.kathttp3.QuicTransportException
import dev.kathttp3.TlsHandshakeException
import kotlinx.coroutines.TimeoutCancellationException

internal data class Http3TransportRequest(
    val method: String,
    val url: String,
    val headers: List<KatHttp3Header>,
    val body: ByteArray? = null,
) {
    fun toKatRequest() = KatHttp3Request(
        method = method,
        url = url,
        headers = headers,
        body = body,
    )
}

internal fun List<KatHttp3Header>.asPairs(): List<Pair<String, String>> =
    map { header -> header.name to header.value }

/** Failures for which retrying the same request through HTTP/2 is appropriate. */
internal fun Throwable.isHttp3TransportFailure(): Boolean =
    isNetworkUnreachable() || generateSequence(this) { it.cause }
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

internal fun Throwable.unreachableAddressFamily(): AddressFamily? =
    generateSequence(this) { it.cause }
        .mapNotNull { error ->
            val match = UNREACHABLE_ADDRESS_PATTERN.find(error.message.orEmpty()) ?: return@mapNotNull null
            val address = match.groupValues[1].removeSurrounding("[", "]")
            when {
                IPV4_ADDRESS_PATTERN.matches(address) -> AddressFamily.Ipv4
                ':' in address -> AddressFamily.Ipv6
                else -> null
            }
        }
        .firstOrNull()

private const val ENETUNREACH = 101
private val UNREACHABLE_ADDRESS_PATTERN =
    Regex("connect\\(\\) to (.+):\\d+ failed: Network is unreachable", RegexOption.IGNORE_CASE)
private val IPV4_ADDRESS_PATTERN = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")

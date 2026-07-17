package app.vimusic.android.utils

import dev.kathttp3.KatHttp3Header
import java.util.Locale

/**
 * Converts application request headers into HTTP/3-safe regular headers.
 *
 * HTTP/3 creates :authority itself, so transport/hop-by-hop headers must not
 * be forwarded by Coil, NewPipe, or the Media3 data source. Keeping this in
 * one place prevents the three HTTP/3 entry points from drifting apart.
 */
internal fun sanitizeHttp3Headers(
    headers: Iterable<Pair<String, String>>,
    defaultAcceptEncoding: String? = null,
    contentLength: Long? = null,
): List<KatHttp3Header> = buildList {
    headers.forEach { (name, value) ->
        val normalized = name.lowercase(Locale.ROOT)
        if (normalized in HTTP3_FORBIDDEN_REQUEST_HEADERS ||
            normalized == CONTENT_LENGTH_HEADER ||
            normalized.any { it <= ' ' || it == ':' } ||
            value.any { it == '\r' || it == '\n' }
        ) {
            return@forEach
        }
        add(KatHttp3Header(normalized, value))
    }
    if (defaultAcceptEncoding != null && none { it.name.equals(ACCEPT_ENCODING_HEADER, ignoreCase = true) }) {
        add(KatHttp3Header(ACCEPT_ENCODING_HEADER, defaultAcceptEncoding))
    }
    contentLength?.let { add(KatHttp3Header(CONTENT_LENGTH_HEADER, it.toString())) }
}

private const val ACCEPT_ENCODING_HEADER = "accept-encoding"
private const val CONTENT_LENGTH_HEADER = "content-length"

private val HTTP3_FORBIDDEN_REQUEST_HEADERS = setOf(
    "connection",
    "keep-alive",
    "proxy-connection",
    "transfer-encoding",
    "upgrade",
    "host",
)

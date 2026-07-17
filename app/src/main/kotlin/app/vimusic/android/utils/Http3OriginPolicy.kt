package app.vimusic.android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.SystemClock
import java.util.Locale

/**
 * Origin-scoped HTTP/3 availability policy shared by playback, extraction, and
 * artwork loading.
 *
 * HTTP/3 is attempted only after the origin advertises it through Alt-Svc (or
 * after a previously validated HTTP/3 response). A transport failure never
 * fails the HTTP operation by itself: it temporarily suppresses H3 and lets
 * the caller use its normal HTTP/2 fallback. This follows the alternative
 * service fallback model in RFC 7838 sections 2.2, 2.4, and 6.
 */
internal object Http3OriginPolicy {
    private val lock = Any()
    private val origins = mutableMapOf<HttpOrigin, OriginState>()
    private var initialized = false
    private var activeNetwork: Network? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            initialized = true
        }
        val connectivity = context.applicationContext
            .getSystemService(ConnectivityManager::class.java)
            ?: return
        activeNetwork = connectivity.activeNetwork
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onNetworkChanged(network)
                override fun onLost(network: Network) {
                    if (activeNetwork == network) onNetworkChanged(null)
                }
            },
        )
    }

    fun shouldAttemptHttp3(url: String): Boolean = origin(url)?.let { origin ->
        synchronized(lock) {
            val state = origins[origin] ?: return false
            val now = SystemClock.elapsedRealtime()
            state.advertisedUntilElapsedMillis > now && state.brokenUntilElapsedMillis <= now
        }
    } ?: false

    fun recordHttpResponse(url: String, status: Int, headers: Iterable<Pair<String, String>>) {
        val origin = origin(url) ?: return
        synchronized(lock) {
            if (status == HTTP_MISDIRECTED_REQUEST) {
                // RFC 7838 section 6: the alternative service is not
                // authoritative for this origin, so remove it immediately.
                origins.remove(origin)
                return
            }
            updateAltSvcLocked(origin, headers)
        }
    }

    fun recordHttp3Success(url: String, status: Int, headers: Iterable<Pair<String, String>>) {
        val origin = origin(url) ?: return
        synchronized(lock) {
            if (status == HTTP_MISDIRECTED_REQUEST) {
                origins.remove(origin)
                return
            }
            val state = origins.getOrPut(origin, ::OriginState)
            updateAltSvcLocked(origin, headers)
            // A successful H3 response remains a usable direct route even if
            // the particular response omitted Alt-Svc.
            state.advertisedUntilElapsedMillis = maxOf(
                state.advertisedUntilElapsedMillis,
                SystemClock.elapsedRealtime() + DEFAULT_VALIDATED_H3_MILLIS,
            )
            state.consecutiveFailures = 0
            state.brokenUntilElapsedMillis = 0
        }
    }

    fun recordHttp3Failure(url: String, failure: Throwable) {
        if (!failure.isHttp3TransportFailure()) return
        val origin = origin(url) ?: return
        synchronized(lock) {
            val state = origins[origin] ?: return
            state.consecutiveFailures = (state.consecutiveFailures + 1).coerceAtMost(MAX_FAILURES)
            val cooldown = if (failure.hasTlsHandshakeFailure()) {
                TLS_FAILURE_COOLDOWN_MILLIS
            } else {
                FAILURE_COOLDOWNS_MILLIS[state.consecutiveFailures - 1]
            }
            state.brokenUntilElapsedMillis = SystemClock.elapsedRealtime() + cooldown
        }
    }

    private fun onNetworkChanged(network: Network?) {
        synchronized(lock) {
            if (network == activeNetwork) return
            activeNetwork = network
            origins.iterator().apply {
                while (hasNext()) {
                    if (!next().value.persistAltSvc) remove()
                }
            }
            origins.values.forEach { state ->
                state.consecutiveFailures = 0
                state.brokenUntilElapsedMillis = 0
            }
        }
    }

    private fun updateAltSvcLocked(origin: HttpOrigin, headers: Iterable<Pair<String, String>>) {
        headers.filter { (name, _) -> name.equals(ALT_SVC_HEADER, ignoreCase = true) }
            .forEach { (_, value) ->
                if (value.trim().equals("clear", ignoreCase = false)) {
                    origins.remove(origin)
                    return
                }
                if (!H3_ALT_SVC_REGEX.containsMatchIn(value)) return@forEach
                val maxAgeSeconds = MAX_AGE_REGEX.find(value)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                    ?: DEFAULT_ALT_SVC_MAX_AGE_SECONDS
                if (maxAgeSeconds <= 0) {
                    origins.remove(origin)
                    return
                }
                val state = origins.getOrPut(origin, ::OriginState)
                state.advertisedUntilElapsedMillis = SystemClock.elapsedRealtime() +
                    maxAgeSeconds.coerceAtMost(MAX_ALT_SVC_MAX_AGE_SECONDS) * 1_000
                state.persistAltSvc = PERSIST_REGEX.containsMatchIn(value)
            }
    }

    private fun origin(url: String): HttpOrigin? = runCatching {
        Uri.parse(url).let { uri ->
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val port = if (uri.port == -1) if (scheme == "https") 443 else 80 else uri.port
            HttpOrigin(scheme, host, port)
        }
    }.getOrNull()

    private data class HttpOrigin(val scheme: String, val host: String, val port: Int)

    private class OriginState {
        var advertisedUntilElapsedMillis = 0L
        var brokenUntilElapsedMillis = 0L
        var consecutiveFailures = 0
        var persistAltSvc = false
    }

    private const val ALT_SVC_HEADER = "alt-svc"
    private const val HTTP_MISDIRECTED_REQUEST = 421
    private const val DEFAULT_ALT_SVC_MAX_AGE_SECONDS = 86_400L
    private const val MAX_ALT_SVC_MAX_AGE_SECONDS = 86_400L
    private const val DEFAULT_VALIDATED_H3_MILLIS = 86_400_000L
    private const val TLS_FAILURE_COOLDOWN_MILLIS = 30 * 60 * 1_000L
    private const val MAX_FAILURES = 4
    private val FAILURE_COOLDOWNS_MILLIS = longArrayOf(
        5_000L,
        30_000L,
        5 * 60 * 1_000L,
        30 * 60 * 1_000L,
    )
    private val H3_ALT_SVC_REGEX = Regex("(?:^|,)\\s*h3(?:-[A-Za-z0-9._-]+)?\\s*=")
    private val MAX_AGE_REGEX = Regex("(?:^|;)\\s*ma\\s*=\\s*\\\"?(\\d+)")
    private val PERSIST_REGEX = Regex("(?:^|;)\\s*persist\\s*=\\s*\\\"?1(?:;|,|$)")
}

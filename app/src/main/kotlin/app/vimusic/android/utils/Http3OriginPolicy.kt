package app.vimusic.android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.math.BigInteger
import java.util.Locale

/**
 * Origin-scoped HTTP/3 availability policy shared by playback, extraction, and
 * artwork loading.
 *
 * kathttp3 currently connects to the request URL's authority. Until it exposes
 * an alternative-authority API, only a formal `h3` advertisement that resolves
 * to the same host and port is usable. This avoids treating an advertised
 * `h3="alt.example:443"` endpoint as if it were `origin.example:443`.
 */
internal object Http3OriginPolicy {
    private val lock = Any()
    private val origins = mutableMapOf<HttpOrigin, OriginState>()
    private var initialized = false
    private var activeNetwork: Network? = null
    private var connectivityManager: ConnectivityManager? = null

    fun initialize(context: Context) {
        val connectivity = context.applicationContext
            .getSystemService(ConnectivityManager::class.java)
            ?: return
        synchronized(lock) {
            if (initialized) return
            initialized = true
            connectivityManager = connectivity
            activeNetwork = connectivity.activeNetwork
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerDefaultNetworkCallback(connectivity)
        }
    }

    fun shouldAttemptHttp3(url: String): Boolean = origin(url)?.let { origin ->
        refreshLegacyDefaultNetwork()
        synchronized(lock) {
            val state = origins[origin] ?: return false
            val now = SystemClock.elapsedRealtime()
            state.alternative.expiresAtElapsedMillis > now && state.brokenUntilElapsedMillis <= now
        }
    } ?: false

    /** Records an origin response; 421 has no special meaning on this path. */
    fun recordOriginResponse(url: String, headers: Iterable<Pair<String, String>>) {
        val origin = origin(url) ?: return
        synchronized(lock) { updateAltSvcLocked(origin, headers) }
    }

    /** Records a response received through the selected HTTP/3 alternative. */
    fun recordHttp3Response(url: String, status: Int, headers: Iterable<Pair<String, String>>) {
        val origin = origin(url) ?: return
        synchronized(lock) {
            if (status == HTTP_MISDIRECTED_REQUEST) {
                // RFC 7838 section 6 applies when the alternative was used.
                origins.remove(origin)
                return
            }
            updateAltSvcLocked(origin, headers)
            origins[origin]?.let { state ->
                state.consecutiveFailures = 0
                state.brokenUntilElapsedMillis = 0
                state.lastValidatedAtElapsedMillis = SystemClock.elapsedRealtime()
            }
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

    @RequiresApi(Build.VERSION_CODES.N)
    private fun registerDefaultNetworkCallback(connectivity: ConnectivityManager) {
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onNetworkChanged(network)

                override fun onLost(network: Network) {
                    synchronized(lock) {
                        if (activeNetwork == network) onNetworkChangedLocked(null)
                    }
                }
            },
        )
    }

    // Android 6.0 lacks registerDefaultNetworkCallback. Sampling activeNetwork
    // before a request still follows the actual default route without reacting
    // to unrelated Wi-Fi/mobile/VPN availability events.
    private fun refreshLegacyDefaultNetwork() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return
        val current = connectivityManager?.activeNetwork
        synchronized(lock) {
            if (current != activeNetwork) onNetworkChangedLocked(current)
        }
    }

    private fun onNetworkChanged(network: Network?) {
        synchronized(lock) { onNetworkChangedLocked(network) }
    }

    private fun onNetworkChangedLocked(network: Network?) {
        if (network == activeNetwork) return
        activeNetwork = network
        val iterator = origins.iterator()
        while (iterator.hasNext()) {
            val state = iterator.next().value
            if (!state.alternative.persist) {
                iterator.remove()
            } else {
                state.consecutiveFailures = 0
                state.brokenUntilElapsedMillis = 0
            }
        }
    }

    /**
     * A received Alt-Svc field replaces the origin's previous alternatives.
     * Missing Alt-Svc leaves cache state unchanged; `clear` or no supported H3
     * alternative removes the cached H3 route.
     */
    private fun updateAltSvcLocked(origin: HttpOrigin, headers: Iterable<Pair<String, String>>) {
        val values = headers
            .filter { (name, _) -> name.equals(ALT_SVC_HEADER, ignoreCase = true) }
            .map { (_, value) -> value }
            .toList()
        if (values.isEmpty()) return
        if (values.any { it.trim() == "clear" }) {
            origins.remove(origin)
            return
        }
        val alternative = values.asSequence()
            .flatMap { parseAltSvc(it).asSequence() }
            .firstOrNull { candidate ->
                candidate.protocolId == HTTP3_ALPN && supportsAuthority(origin, candidate)
            }
        if (alternative == null) {
            origins.remove(origin)
            return
        }
        val previous = origins[origin]
        origins[origin] = OriginState(
            alternative = alternative,
            consecutiveFailures = previous?.consecutiveFailures ?: 0,
            brokenUntilElapsedMillis = previous?.brokenUntilElapsedMillis ?: 0,
            lastValidatedAtElapsedMillis = previous?.lastValidatedAtElapsedMillis ?: 0,
        )
    }

    private fun supportsAuthority(origin: HttpOrigin, alternative: AlternativeService): Boolean =
        alternative.host?.equals(origin.host, ignoreCase = true) != false && alternative.port == origin.port

    /** Parses only well-formed quoted Alt-Svc alternatives. */
    private fun parseAltSvc(value: String): List<AlternativeService> = splitAlternatives(value)
        .mapNotNull { part -> parseAlternative(part) }

    private fun splitAlternatives(value: String): List<String> {
        val parts = mutableListOf<String>()
        var quoted = false
        var start = 0
        value.forEachIndexed { index, character ->
            when (character) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) {
                    parts += value.substring(start, index)
                    start = index + 1
                }
            }
        }
        if (!quoted) parts += value.substring(start)
        return parts
    }

    private fun parseAlternative(part: String): AlternativeService? {
        val match = ALT_VALUE_REGEX.matchEntire(part.trim()) ?: return null
        val protocolId = match.groupValues[1]
        val authority = parseAuthority(match.groupValues[2]) ?: return null
        val parameters = match.groupValues[3]
        val expiresAtElapsedMillis = expiryFromMaxAge(parameterValue(parameters, "ma")) ?: return null
        return AlternativeService(
            protocolId = protocolId,
            host = authority.host,
            port = authority.port,
            expiresAtElapsedMillis = expiresAtElapsedMillis,
            persist = parameterValue(parameters, "persist") == "1",
        )
    }

    /** Uses the server's ma while saturating before unit conversion/addition. */
    private fun expiryFromMaxAge(value: String?): Long? {
        val seconds = runCatching {
            BigInteger(value ?: DEFAULT_ALT_SVC_MAX_AGE_SECONDS.toString())
        }.getOrNull() ?: return null
        if (seconds <= BigInteger.ZERO) return null
        val now = SystemClock.elapsedRealtime()
        val maxDurationMillis = BigInteger.valueOf(Long.MAX_VALUE - now)
        val durationMillis = seconds.multiply(MILLIS_PER_SECOND).min(maxDurationMillis)
        return now + durationMillis.toLong()
    }

    private fun parseAuthority(value: String): AlternativeAuthority? {
        val separator = value.lastIndexOf(':')
        if (separator < 0) return null
        val host = value.substring(0, separator).ifBlank { null }
        val port = value.substring(separator + 1).toIntOrNull() ?: return null
        return AlternativeAuthority(host, port)
    }

    private fun parameterValue(parameters: String, name: String): String? = parameters
        .split(';')
        .asSequence()
        .map(String::trim)
        .mapNotNull { parameter ->
            val separator = parameter.indexOf('=')
            if (separator < 0) null else parameter.substring(0, separator) to parameter.substring(separator + 1)
        }
        .firstOrNull { (parameterName, _) -> parameterName.equals(name, ignoreCase = true) }
        ?.second
        ?.trim()
        ?.removeSurrounding("\"")

    private fun origin(url: String): HttpOrigin? = runCatching {
        Uri.parse(url).let { uri ->
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val port = if (uri.port == -1) if (scheme == "https") 443 else 80 else uri.port
            HttpOrigin(scheme, host, port)
        }
    }.getOrNull()

    private data class HttpOrigin(val scheme: String, val host: String, val port: Int)

    private data class AlternativeAuthority(val host: String?, val port: Int)

    private data class AlternativeService(
        val protocolId: String,
        val host: String?,
        val port: Int,
        val expiresAtElapsedMillis: Long,
        val persist: Boolean,
    )

    private data class OriginState(
        val alternative: AlternativeService,
        var consecutiveFailures: Int = 0,
        var brokenUntilElapsedMillis: Long = 0,
        var lastValidatedAtElapsedMillis: Long = 0,
    )

    private const val ALT_SVC_HEADER = "alt-svc"
    private const val HTTP3_ALPN = "h3"
    private const val HTTP_MISDIRECTED_REQUEST = 421
    private const val DEFAULT_ALT_SVC_MAX_AGE_SECONDS = 86_400L
    private const val TLS_FAILURE_COOLDOWN_MILLIS = 30 * 60 * 1_000L
    private const val MAX_FAILURES = 4
    private val MILLIS_PER_SECOND = BigInteger.valueOf(1_000L)
    private val FAILURE_COOLDOWNS_MILLIS = longArrayOf(
        5_000L,
        30_000L,
        5 * 60 * 1_000L,
        30 * 60 * 1_000L,
    )
    private val ALT_VALUE_REGEX = Regex("([A-Za-z0-9._-]+)\\s*=\\s*\\\"([^\\\"]*)\\\"(.*)")
}

package app.vimusic.android.utils

import android.content.Context
import android.os.Build
import android.util.Log
import app.vimusic.providers.utils.ProviderHttpClient
import dev.kathttp3.KatHttp3Client
import dev.kathttp3.KatHttp3ClientConfig
import dev.kathttp3.KatHttp3RetryPolicy
import dev.kathttp3.PlatformDnsResolver
import dev.kathttp3.PolicyRetryInterceptor
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.kathttp3.createKatHttp3Client

private const val KAT_HTTP3_TAG = "KatHttp3Ktor"

/**
 * Installs the kathttp3 HTTP/3 transport for provider Ktor clients on every supported API 26+
 * device. Returns true when installed.
 */
fun installKatHttp3KtorClientIfSupported(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return runCatching {
        val client = providerClient(context.applicationContext)
        ProviderHttpClient.install { block: HttpClientConfig<*>.() -> Unit ->
            createKatHttp3Client(
                config = {
                    this.client = client
                    maxConcurrentRequests = 8
                },
                block = block,
            )
        }
        true
    }.getOrElse { error ->
        Log.w(KAT_HTTP3_TAG, "kathttp3 transport unavailable; retaining default HTTP stack", error)
        false
    }
}

private fun providerClient(context: Context): KatHttp3Client = synchronized(KatHttp3KtorClientHolder) {
    KatHttp3KtorClientHolder.client ?: KatHttp3Client(
        config = KatHttp3ClientConfig(
            maxActiveStreamsPerOrigin = 8,
            maxQueuedRequestsPerOrigin = 128,
            queueTimeoutMillis = 120_000,
            responseHeadersTimeoutMillis = 45_000,
            readTimeoutMillis = 90_000,
            callTimeoutMillis = 120_000,
            enable0Rtt = true,
            qlogEnabled = false,
            insecureCert = false,
            // Android's resolver honors Private DNS and the active network.
            // Avoid native getaddrinfo, which can fail for app-scoped DNS.
            resolver = PlatformDnsResolver(),
            interceptors = listOf(
                PolicyRetryInterceptor(
                    KatHttp3RetryPolicy(
                        maxAttempts = 5,
                        initialBackoffMillis = 300,
                        maxBackoffMillis = 500,
                    ),
                ),
            ),
        ),
        applicationContext = context,
    ).also { KatHttp3KtorClientHolder.client = it }
}

private object KatHttp3KtorClientHolder {
    var client: KatHttp3Client? = null
}

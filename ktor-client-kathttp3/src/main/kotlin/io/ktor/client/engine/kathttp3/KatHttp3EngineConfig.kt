package io.ktor.client.engine.kathttp3

import dev.kathttp3.KatHttp3Client
import dev.kathttp3.KatHttp3ClientConfig
import io.ktor.client.engine.HttpClientEngineConfig

/** Configuration for the kathttp3 (ngtcp2/nghttp3/BoringSSL) HTTP/3 transport. */
public class KatHttp3EngineConfig : HttpClientEngineConfig() {
    /** Underlying kathttp3 client configuration used when [client] is not supplied. */
    public var clientConfig: KatHttp3ClientConfig = KatHttp3ClientConfig()

    /**
     * Maximum simultaneous requests submitted to a single kathttp3 client.
     *
     * kathttp3 uses one HTTP/3 connection per origin. Limiting the number of
     * active streams keeps image-heavy Coil screens from exhausting that
     * connection's receive windows.
     */
    public var maxConcurrentRequests: Int = 4
        set(value) {
            require(value > 0) { "maxConcurrentRequests must be greater than zero" }
            field = value
        }

    /**
     * Optional externally-owned [KatHttp3Client]. When set the engine reuses it and does not close
     * it; otherwise the engine creates and owns a client built from [clientConfig].
     */
    public var client: KatHttp3Client? = null
}

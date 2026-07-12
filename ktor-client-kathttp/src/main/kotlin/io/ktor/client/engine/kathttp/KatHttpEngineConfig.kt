package io.ktor.client.engine.kathttp

import dev.kathttp.KatHttpClient
import dev.kathttp.KatHttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig

/** Configuration for the kathttp (ngtcp2/nghttp3/BoringSSL) HTTP/3 transport. */
public class KatHttpEngineConfig : HttpClientEngineConfig() {
    /** Underlying kathttp client configuration used when [client] is not supplied. */
    public var clientConfig: KatHttpClientConfig = KatHttpClientConfig()

    /**
     * Optional externally-owned [KatHttpClient]. When set the engine reuses it and does not close
     * it; otherwise the engine creates and owns a client built from [clientConfig].
     */
    public var client: KatHttpClient? = null
}

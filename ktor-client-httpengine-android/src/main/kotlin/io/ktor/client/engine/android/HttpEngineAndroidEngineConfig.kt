package io.ktor.client.engine.android

import io.ktor.client.engine.HttpClientEngineConfig
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

/** Local equivalent of Ktor's Android configuration with an injectable connection factory. */
public class HttpEngineAndroidEngineConfig : HttpClientEngineConfig() {
    public var connectTimeout: Int = 100_000
    public var socketTimeout: Int = 100_000
    public var sslManager: (HttpsURLConnection) -> Unit = {}
    public var requestConfig: HttpURLConnection.() -> Unit = {}
    public lateinit var connectionFactory: HttpEngineConnectionFactory
}

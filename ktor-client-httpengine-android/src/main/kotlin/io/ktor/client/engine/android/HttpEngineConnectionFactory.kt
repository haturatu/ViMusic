package io.ktor.client.engine.android

import android.net.http.HttpEngine
import java.net.HttpURLConnection
import java.net.URL

/** Supplies Android 14+'s platform HTTP stack without changing Ktor's call lifecycle. */
public fun interface HttpEngineConnectionFactory {
    public fun open(url: URL): HttpURLConnection
}

public fun HttpEngine.asKtorConnectionFactory(): HttpEngineConnectionFactory =
    HttpEngineConnectionFactory { url -> openConnection(url) as HttpURLConnection }

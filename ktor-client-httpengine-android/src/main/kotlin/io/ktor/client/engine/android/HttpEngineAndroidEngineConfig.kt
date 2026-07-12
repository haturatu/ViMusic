package io.ktor.client.engine.android

import io.ktor.client.engine.HttpClientEngineConfig

/** Configuration for the direct Android HttpEngine transport. */
public class HttpEngineAndroidEngineConfig : HttpClientEngineConfig() {
    public lateinit var httpEngine: android.net.http.HttpEngine
}

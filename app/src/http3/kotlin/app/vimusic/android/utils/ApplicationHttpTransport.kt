package app.vimusic.android.utils

import android.content.Context
import android.os.Build
import coil3.ComponentRegistry
import coil3.network.NetworkFetcher

internal fun initializeHttpTransport(context: Context) {
    Http3OriginPolicy.initialize(context)
}

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
internal fun ComponentRegistry.Builder.addHttpTransport(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        add(
            NetworkFetcher.Factory(
                networkClient = { KatHttp3CoilNetworkClient(context.applicationContext) },
                concurrentRequestStrategy = { KatHttp3CoilConcurrentRequestStrategy },
            ),
        )
    }
}

package app.vimusic.android.ui.components

import app.vimusic.android.utils.isHttp3TransportFailure

internal fun Throwable.isHttpTransportFailure(): Boolean = isHttp3TransportFailure()

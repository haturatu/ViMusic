package app.vimusic.android.service

import android.content.Context
import androidx.media3.datasource.HttpDataSource
import app.vimusic.android.utils.KatHttp3MediaDataSource

internal fun playbackHttpDataSourceFactory(context: Context): HttpDataSource.Factory =
    KatHttp3MediaDataSource.Factory(context)

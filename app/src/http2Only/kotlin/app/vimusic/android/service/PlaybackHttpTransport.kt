package app.vimusic.android.service

import android.content.Context
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource

internal fun playbackHttpDataSourceFactory(context: Context): HttpDataSource.Factory =
    DefaultHttpDataSource.Factory()

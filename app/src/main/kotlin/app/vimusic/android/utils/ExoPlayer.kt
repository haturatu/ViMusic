@file:OptIn(UnstableApi::class)

package app.vimusic.android.utils

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource

val Cache.asDataSource
    get() = CacheDataSource.Factory()
        .setCache(this)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

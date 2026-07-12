package app.vimusic.android.utils

import android.content.Context
import android.net.http.HttpEngine
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpEngineDataSource
import app.vimusic.android.extractor.HttpEngineDownloader
import org.schabi.newpipe.extractor.downloader.Downloader
import java.util.concurrent.Executor

/** Shared Android platform HTTP/3-over-QUIC stack for Android 14 and newer. */
object HttpEngineProvider {
    private const val TAG = "HttpEngine"
    private val executor = Executor(Runnable::run)

    /** Returns the shared [HttpEngine], or null when HTTP/3 is unavailable (pre-API 34 or build failure). */
    fun engine(context: Context): HttpEngine? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Api34.engine(context)
        } else {
            null
        }

    /** media3 [HttpDataSource.Factory] backed by [HttpEngine] (falls back to [DefaultHttpDataSource]). */
    fun dataSourceFactory(context: Context): HttpDataSource.Factory {
        val httpEngine = engine(context) ?: return DefaultHttpDataSource.Factory()
        return runCatching { HttpEngineDataSource.Factory(httpEngine, executor) }
            .getOrElse { error ->
                Log.w(TAG, "HttpEngine data source unavailable; using default HTTP stack", error)
                DefaultHttpDataSource.Factory()
            }
    }

    /**
     * [Downloader] backed by [HttpEngine] for the generic NewPipe extractor requests.
     * Returns null when HTTP/3 is unavailable so callers can fall back to the OkHttp downloader.
     */
    fun downloader(context: Context): Downloader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Api34.downloader(context)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34 {
        @Volatile private var engine: HttpEngine? = null

        fun engine(context: Context): HttpEngine = synchronized(this) {
            engine ?: HttpEngine.Builder(context.applicationContext)
                .setEnableHttp2(true)
                .setEnableQuic(true)
                .build()
                .also { engine = it }
        }

        fun downloader(context: Context): Downloader? = runCatching {
            HttpEngineDownloader(engine(context))
        }.getOrElse { error ->
            Log.w(TAG, "HttpEngine downloader unavailable; using default HTTP stack", error)
            null
        }
    }
}

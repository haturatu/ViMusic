package app.vimusic.android.utils

import android.content.Context
import android.net.http.HttpEngine
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpEngineDataSource
import java.util.concurrent.Executor

/** Uses Android's platform HTTP/3-over-QUIC stack on Android 14 and newer. */
object HttpEngineDataSourceProvider {
    private const val TAG = "HttpEngineDataSource"

    fun factory(context: Context): HttpDataSource.Factory =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Api34.factory(context)
        } else {
            DefaultHttpDataSource.Factory()
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34 {
        private val executor = Executor(Runnable::run)
        @Volatile private var engine: HttpEngine? = null

        fun factory(context: Context): HttpDataSource.Factory = runCatching<HttpDataSource.Factory> {
            val httpEngine = engine ?: synchronized(this) {
                engine ?: HttpEngine.Builder(context.applicationContext)
                    .setEnableHttp2(true)
                    .setEnableQuic(true)
                    .build()
                    .also { engine = it }
            }
            HttpEngineDataSource.Factory(httpEngine, executor)
        }.getOrElse { error ->
            Log.w(TAG, "HttpEngine unavailable; using default HTTP stack", error)
            DefaultHttpDataSource.Factory()
        }
    }
}

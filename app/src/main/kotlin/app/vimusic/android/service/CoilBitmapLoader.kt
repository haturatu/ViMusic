@file:Suppress("TooGenericExceptionCaught") // Return image failures through the Media3 future.

package app.vimusic.android.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.BitmapLoader
import app.vimusic.android.preferences.AppearancePreferences
import app.vimusic.android.utils.thumbnail
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

/** The same thumbnail size and shared Coil loader used by the phone player. */
internal fun playerArtworkSize(context: Context): Int = with(context.resources) {
    ((minOf(configuration.screenWidthDp, configuration.screenHeightDp) - 64).coerceAtLeast(1) * displayMetrics.density)
        .roundToInt().coerceIn(1, AppearancePreferences.maxThumbnailSize.coerceAtLeast(1))
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CoilBitmapLoader(context: Context, private val scope: CoroutineScope) : BitmapLoader {
    private val context = context.applicationContext
    override fun supportsMimeType(mimeType: String) = MimeTypes.isImage(mimeType)
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = future { load(data) }
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = future { load(uri) }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val uri = metadata.artworkUri
        val data = metadata.artworkData
        if (uri == null && data == null) return null
        return future {
            // The embedded bytes are sized for notifications, not a car playback screen.
            // Prefer the original URI; retain the bytes as an offline fallback.
            try { load(uri ?: requireNotNull(data)) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (uri != null && data != null) load(data) else throw error }
        }
    }

    private suspend fun load(data: Any): Bitmap {
        val size = playerArtworkSize(context)
        val request = ImageRequest.Builder(context)
            .data(if (data is Uri) data.thumbnail(size) else data)
            .size(size, size).allowHardware(false).build()
        return when (val result = context.imageLoader.execute(request)) {
            is SuccessResult -> result.image.toBitmap()
            is ErrorResult -> throw IOException("Unable to load session artwork", result.throwable)
        }
    }

    private fun future(block: suspend () -> Bitmap): ListenableFuture<Bitmap> {
        val result = SettableFuture.create<Bitmap>()
        val job = scope.launch {
            try { result.set(block()) }
            catch (error: CancellationException) { result.cancel(false); throw error }
            catch (error: Exception) { result.setException(error) }
        }
        job.invokeOnCompletion { if (it is CancellationException) result.cancel(false) }
        result.addListener({ if (result.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return result
    }
}

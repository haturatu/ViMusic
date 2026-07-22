package app.vimusic.android.extractor

import android.content.Context
import android.os.Build
import org.schabi.newpipe.extractor.downloader.Downloader

internal fun createSystemDownloader(
    fallback: NewPipeDownloader,
    context: Context?,
): Downloader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    KatHttp3Downloader(fallback, context)
} else {
    fallback
}

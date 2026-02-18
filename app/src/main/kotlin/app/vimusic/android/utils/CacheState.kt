package app.vimusic.android.utils

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.FileDataSource
import app.vimusic.android.Database
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.models.Format
import app.vimusic.android.service.LOCAL_KEY_PREFIX
import app.vimusic.android.service.PlayerService
import app.vimusic.android.service.PrecacheService
import app.vimusic.android.service.downloadState
import app.vimusic.android.ui.components.themed.CircularProgressIndicator
import app.vimusic.android.ui.components.themed.HeaderIconButton
import app.vimusic.core.ui.LocalAppearance
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.FileNotFoundException

@Composable
fun PlaylistDownloadIcon(
    songs: ImmutableList<MediaItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (colorPalette) = LocalAppearance.current

    val isDownloading by downloadState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = isDownloading,
        label = "",
        transitionSpec = { fadeIn() togetherWith fadeOut() }
    ) { currentIsDownloading ->
        when {
            currentIsDownloading -> CircularProgressIndicator(modifier = Modifier.size(18.dp))

            !songs.map { it.mediaId }.fastAll {
                isCached(
                    mediaId = it,
                    key = isDownloading
                )
            } -> HeaderIconButton(
                icon = R.drawable.download,
                color = colorPalette.text,
                onClick = {
                    songs.forEach {
                        PrecacheService.scheduleCache(context.applicationContext, it)
                    }
                },
                modifier = modifier
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun isCached(
    mediaId: String,
    key: Any? = Unit,
    binder: PlayerService.Binder? = LocalPlayerServiceBinder.current
): Boolean {
    if (mediaId.startsWith(LOCAL_KEY_PREFIX)) return true

    var format: Format? by remember { mutableStateOf(null) }

    LaunchedEffect(mediaId, key) {
        Database
            .format(mediaId)
            .distinctUntilChanged()
            .collect { format = it }
    }

    return remember(mediaId, binder, format, key) {
        format?.contentLength?.let { len ->
            binder?.cache?.isCached(mediaId, 0, len)
        } ?: false
    }
}

@OptIn(UnstableApi::class)
class ConditionalCacheDataSourceFactory(
    private val cacheDataSourceFactory: CacheDataSource.Factory,
    private val upstreamDataSourceFactory: DataSource.Factory,
    private val shouldCache: (DataSpec) -> Boolean
) : DataSource.Factory {
    init {
        cacheDataSourceFactory.setUpstreamDataSourceFactory(upstreamDataSourceFactory)
    }

    override fun createDataSource() = object : DataSource {
        private var selectedFactory: DataSource.Factory = upstreamDataSourceFactory
        private val transferListeners = mutableListOf<TransferListener>()
        private var source: DataSource? = null

        private fun createSource(factory: DataSource.Factory) = factory.createDataSource().apply {
            transferListeners.forEach { addTransferListener(it) }
        }

        private fun currentSource(): DataSource = source ?: createSource(selectedFactory).also { source = it }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = runCatching {
            currentSource().read(buffer, offset, length)
        }.getOrElse { error ->
            if (
                selectedFactory === cacheDataSourceFactory &&
                (error is FileDataSource.FileDataSourceException ||
                        error.findCause<FileNotFoundException>() != null)
            ) {
                // Cache file disappeared; fall back to upstream and retry once.
                selectedFactory = upstreamDataSourceFactory
                source = createSource(upstreamDataSourceFactory)
                currentSource().read(buffer, offset, length)
            } else {
                throw error
            }
        }

        override fun addTransferListener(transferListener: TransferListener) {
            source?.addTransferListener(transferListener)
            transferListeners += transferListener
        }

        override fun open(dataSpec: DataSpec): Long {
            selectedFactory =
                if (shouldCache(dataSpec)) cacheDataSourceFactory else upstreamDataSourceFactory
            source = createSource(selectedFactory)

            return runCatching {
                currentSource().open(dataSpec)
            }.getOrElse {
                if (
                    it is ReadOnlyException ||
                    it.findCause<ReadOnlyException>() != null ||
                    it is FileDataSource.FileDataSourceException ||
                    it.findCause<FileNotFoundException>() != null
                ) {
                    selectedFactory = upstreamDataSourceFactory
                    source = createSource(upstreamDataSourceFactory)
                    currentSource().open(dataSpec)
                } else throw it
            }
        }

        override fun getUri() = source?.uri
        override fun close() {
            source?.close()
            source = null
        }
    }
}

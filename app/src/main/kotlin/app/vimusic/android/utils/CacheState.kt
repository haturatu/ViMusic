package app.vimusic.android.utils

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Composable
fun PlaylistDownloadIcon(
    songs: ImmutableList<MediaItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (colorPalette) = LocalAppearance.current

    val isDownloading by downloadState.collectAsState()

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
        private lateinit var selectedFactory: DataSource.Factory
        private val transferListeners = mutableListOf<TransferListener>()

        private fun createSource(factory: DataSource.Factory = selectedFactory) = factory.createDataSource().apply {
            transferListeners.forEach { addTransferListener(it) }
        }

        private var source by object : ReadWriteProperty<Any?, DataSource> {
            var s: DataSource? = null

            override fun getValue(thisRef: Any?, property: KProperty<*>) = s ?: run {
                val newSource = createSource()
                s = newSource
                newSource
            }

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: DataSource) {
                s = value
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int) =
            source.read(buffer, offset, length)

        override fun addTransferListener(transferListener: TransferListener) {
            if (::selectedFactory.isInitialized) source.addTransferListener(transferListener)

            transferListeners += transferListener
        }

        override fun open(dataSpec: DataSpec): Long {
            selectedFactory =
                if (shouldCache(dataSpec)) cacheDataSourceFactory else upstreamDataSourceFactory

            return runCatching {
                source.open(dataSpec)
            }.getOrElse {
                if (it is ReadOnlyException) {
                    source = createSource(upstreamDataSourceFactory)
                    source.open(dataSpec)
                } else throw it
            }
        }

        override fun getUri() = source.uri
        override fun close() = source.close()
    }
}

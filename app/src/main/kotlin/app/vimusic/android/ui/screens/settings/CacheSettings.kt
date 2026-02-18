package app.vimusic.android.ui.screens.settings

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.preferences.PlayerPreferences
import app.vimusic.android.ui.components.themed.LinearProgressIndicator
import app.vimusic.android.ui.screens.Route
import app.vimusic.android.utils.toast
import app.vimusic.core.data.enums.ExoPlayerDiskCacheSize
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(UnstableApi::class)
@Route
@Composable
fun CacheSettings() = with(DataPreferences) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val coroutineScope = rememberCoroutineScope()
    val cacheCleared = stringResource(R.string.cache_cleared)

    SettingsCategoryScreen(title = stringResource(R.string.cache)) {
        SettingsDescription(text = stringResource(R.string.cache_description))

        context.imageLoader.diskCache?.let { diskCache ->
            val diskCacheSize by remember { derivedStateOf { diskCache.size } }
            val maxSizeBytes = coilDiskCacheMaxSize.bytes
            val formattedSize = remember(diskCacheSize, maxSizeBytes) {
                "${formatMiB(diskCacheSize)} / ${formatMiB(maxSizeBytes)}"
            }
            val sizePercentage = remember(diskCacheSize, coilDiskCacheMaxSize) {
                diskCacheSize.toFloat() / coilDiskCacheMaxSize.bytes.coerceAtLeast(1)
            }

            SettingsGroup(
                title = stringResource(R.string.image_cache),
                description = stringResource(
                    R.string.format_cache_space_used_percentage,
                    formattedSize,
                    (sizePercentage * 100).toInt()
                )
            ) {
                LinearProgressIndicator(
                    progress = sizePercentage,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .padding(start = 32.dp, end = 16.dp)
                )
                EnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.max_size),
                    selectedValue = coilDiskCacheMaxSize,
                    onValueSelect = { coilDiskCacheMaxSize = it }
                )
                SettingsEntry(
                    title = stringResource(R.string.clear_image_cache),
                    text = stringResource(R.string.clear_image_cache_description),
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            diskCache.clear()
                            withContext(Dispatchers.Main) {
                                context.toast(cacheCleared)
                            }
                        }
                    }
                )
            }
        }
        binder?.cache?.let { cache ->
            val diskCacheSize by remember { derivedStateOf { cache.cacheSpace } }
            val formattedSize = remember(diskCacheSize, exoPlayerDiskCacheMaxSize) {
                if (exoPlayerDiskCacheMaxSize == ExoPlayerDiskCacheSize.Unlimited) {
                    formatMiB(diskCacheSize)
                } else {
                    "${formatMiB(diskCacheSize)} / ${formatMiB(exoPlayerDiskCacheMaxSize.bytes)}"
                }
            }
            val sizePercentage = remember(diskCacheSize, exoPlayerDiskCacheMaxSize) {
                diskCacheSize.toFloat() / exoPlayerDiskCacheMaxSize.bytes.coerceAtLeast(1)
            }

            SettingsGroup(
                title = stringResource(R.string.song_cache),
                description = if (exoPlayerDiskCacheMaxSize == ExoPlayerDiskCacheSize.Unlimited) stringResource(
                    R.string.format_cache_space_used,
                    formattedSize
                )
                else stringResource(
                    R.string.format_cache_space_used_percentage,
                    formattedSize,
                    (sizePercentage * 100).toInt()
                )
            ) {
                AnimatedVisibility(visible = exoPlayerDiskCacheMaxSize != ExoPlayerDiskCacheSize.Unlimited) {
                    LinearProgressIndicator(
                        progress = sizePercentage,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .padding(start = 32.dp, end = 16.dp)
                    )
                }
                EnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.max_size),
                    selectedValue = exoPlayerDiskCacheMaxSize,
                    onValueSelect = { newSize ->
                        exoPlayerDiskCacheMaxSize = newSize

                        coroutineScope.launch(Dispatchers.IO) {
                            trimExoCacheIfNeeded(cache = cache, maxBytes = newSize.bytes)
                        }
                    }
                )
                SwitchSettingsEntry(
                    title = stringResource(R.string.pause_song_cache),
                    text = stringResource(R.string.pause_song_cache_description),
                    isChecked = PlayerPreferences.pauseCache,
                    onCheckedChange = { PlayerPreferences.pauseCache = it }
                )
                SwitchSettingsEntry(
                    title = stringResource(R.string.cache_favorites_only),
                    text = stringResource(R.string.cache_favorites_only_description),
                    isChecked = cacheFavoritesOnly,
                    onCheckedChange = { cacheFavoritesOnly = it }
                )
                SettingsEntry(
                    title = stringResource(R.string.clear_song_cache),
                    text = stringResource(R.string.clear_song_cache_description),
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            cache.keys.toList().forEach { key ->
                                cache.removeResource(key)
                            }
                            withContext(Dispatchers.Main) {
                                context.toast(cacheCleared)
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun formatMiB(bytes: Long): String {
    val value = bytes.toDouble() / 1_048_576.0
    return String.format(Locale.US, "%.1f MiB", value)
}

@OptIn(UnstableApi::class)
private fun trimExoCacheIfNeeded(cache: Cache, maxBytes: Long) {
    if (maxBytes <= 0L || cache.cacheSpace <= maxBytes) return

    var current = cache.cacheSpace
    val spans = cache.keys
        .asSequence()
        .flatMap { key -> cache.getCachedSpans(key).asSequence() }
        .sortedBy(CacheSpan::lastTouchTimestamp)
        .toList()

    spans.forEach { span ->
        if (current <= maxBytes) return

        runCatching {
            cache.removeSpan(span)
            current -= span.length
        }.onFailure {
            runCatching { cache.removeResource(span.key) }
            current = cache.cacheSpace
        }
    }

    if (current <= maxBytes) return

    cache.keys.toList().forEach { key ->
        if (current <= maxBytes) return
        runCatching { cache.removeResource(key) }
        current = cache.cacheSpace
    }
}

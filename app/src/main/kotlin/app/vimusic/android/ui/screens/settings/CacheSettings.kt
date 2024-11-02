package app.vimusic.android.ui.screens.settings

import android.text.format.Formatter
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.vimusic.android.LocalPlayerServiceBinder
import app.vimusic.android.R
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.preferences.PlayerPreferences
import app.vimusic.android.ui.components.themed.LinearProgressIndicator
import app.vimusic.android.ui.screens.Route
import app.vimusic.core.data.enums.ExoPlayerDiskCacheSize
import coil3.imageLoader

@OptIn(UnstableApi::class)
@Route
@Composable
fun CacheSettings() = with(DataPreferences) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current

    SettingsCategoryScreen(title = stringResource(R.string.cache)) {
        SettingsDescription(text = stringResource(R.string.cache_description))

        context.imageLoader.diskCache?.let { diskCache ->
            val diskCacheSize by remember { derivedStateOf { diskCache.size } }
            val formattedSize = remember(diskCacheSize) {
                Formatter.formatShortFileSize(context, diskCacheSize)
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
            }
        }
        binder?.cache?.let { cache ->
            val diskCacheSize by remember { derivedStateOf { cache.cacheSpace } }
            val formattedSize = remember(diskCacheSize) {
                Formatter.formatShortFileSize(context, diskCacheSize)
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
                    onValueSelect = { exoPlayerDiskCacheMaxSize = it }
                )
                SwitchSettingsEntry(
                    title = stringResource(R.string.pause_song_cache),
                    text = stringResource(R.string.pause_song_cache_description),
                    isChecked = PlayerPreferences.pauseCache,
                    onCheckedChange = { PlayerPreferences.pauseCache = it }
                )
            }
        }
    }
}

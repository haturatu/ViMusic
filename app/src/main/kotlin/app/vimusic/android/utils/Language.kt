package app.vimusic.android.utils

import android.app.Activity
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import app.vimusic.core.ui.utils.isCompositionLaunched
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun currentLocale(): Locale? {
    val context = LocalContext.current
    var locale: Locale? by remember { mutableStateOf(null) }
    LaunchedEffect(isCompositionLaunched()) {
        locale = runCatching {
            context.getSystemService<LocaleManager>()?.applicationLocales?.get(0)
        }.getOrNull()
    }
    return locale
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Activity.startLanguagePicker() = startActivity(
    Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
)

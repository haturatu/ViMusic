package app.vimusic.android.ui.screens.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.vimusic.android.Database
import app.vimusic.android.R
import app.vimusic.android.internal
import app.vimusic.android.path
import app.vimusic.android.preferences.DataPreferences
import app.vimusic.android.query
import app.vimusic.android.service.PlayerService
import app.vimusic.android.transaction
import app.vimusic.android.ui.screens.Route
import app.vimusic.android.utils.intent
import app.vimusic.android.utils.toast
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

@Route
@Composable
fun DatabaseSettings() = with(DataPreferences) {
    val context = LocalContext.current

    val eventsCount by remember { Database.eventsCount().distinctUntilChanged() }
        .collectAsState(initial = 0)

    val blacklistLength by remember { Database.blacklistLength().distinctUntilChanged() }
        .collectAsState(initial = 0)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType = "application/vnd.sqlite3")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        query {
            Database.checkpoint()

            context.applicationContext.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(Database.internal.path).use { input -> input.copyTo(output) }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        query {
            Database.checkpoint()
            Database.internal.close()

            context.applicationContext.contentResolver.openInputStream(uri)
                ?.use { inputStream ->
                    FileOutputStream(Database.internal.path).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

            context.stopService(context.intent<PlayerService>())
            exitProcess(0)
        }
    }

    SettingsCategoryScreen(title = stringResource(R.string.database)) {
        SettingsGroup(title = stringResource(R.string.cleanup)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.pause_playback_history),
                text = stringResource(R.string.pause_playback_history_description),
                isChecked = pauseHistory,
                onCheckedChange = { pauseHistory = !pauseHistory }
            )

            AnimatedVisibility(visible = pauseHistory) {
                SettingsDescription(
                    text = stringResource(R.string.pause_playback_history_warning),
                    important = true
                )
            }

            AnimatedVisibility(visible = !(pauseHistory && eventsCount == 0)) {
                SettingsEntry(
                    title = stringResource(R.string.reset_quick_picks),
                    text = if (eventsCount > 0) pluralStringResource(
                        R.plurals.format_reset_quick_picks_amount,
                        eventsCount,
                        eventsCount
                    )
                    else stringResource(R.string.quick_picks_empty),
                    onClick = { query(Database::clearEvents) },
                    isEnabled = eventsCount > 0
                )
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.pause_playback_time),
                text = stringResource(
                    R.string.format_pause_playback_time_description,
                    topListLength
                ),
                isChecked = pausePlaytime,
                onCheckedChange = { pausePlaytime = !pausePlaytime }
            )

            SettingsEntry(
                title = stringResource(R.string.reset_blacklist),
                text = if (blacklistLength > 0) pluralStringResource(
                    R.plurals.format_reset_blacklist_description,
                    blacklistLength,
                    blacklistLength
                ) else stringResource(R.string.blacklist_empty),
                isEnabled = blacklistLength > 0,
                onClick = {
                    transaction {
                        Database.resetBlacklist()
                    }
                }
            )
        }
        SettingsGroup(
            title = stringResource(R.string.backup),
            description = stringResource(R.string.backup_description)
        ) {
            SettingsEntry(
                title = stringResource(R.string.backup),
                text = stringResource(R.string.backup_action_description),
                onClick = {
                    val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())

                    try {
                        backupLauncher.launch("ViMusic_backup_${dateFormat.format(Date())}.db")
                    } catch (e: ActivityNotFoundException) {
                        context.toast(context.getString(R.string.no_file_chooser_installed))
                    }
                }
            )
        }
        SettingsGroup(
            title = stringResource(R.string.restore),
            description = stringResource(R.string.restore_warning),
            important = true
        ) {
            SettingsEntry(
                title = stringResource(R.string.restore),
                text = stringResource(R.string.restore_description),
                onClick = {
                    try {
                        restoreLauncher.launch(
                            arrayOf(
                                "application/vnd.sqlite3",
                                "application/x-sqlite3",
                                "application/octet-stream"
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        context.toast(context.getString(R.string.no_file_chooser_installed))
                    }
                }
            )
        }
    }
}

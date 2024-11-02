package app.vimusic.android.utils

import android.app.Activity
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Log
import android.util.Rational
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.OnPictureInPictureModeChangedProvider
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.graphics.toRect
import app.vimusic.android.R
import app.vimusic.android.preferences.AppearancePreferences
import app.vimusic.compose.persist.findActivityNullable
import app.vimusic.core.ui.utils.isAtLeastAndroid12
import app.vimusic.core.ui.utils.isAtLeastAndroid7
import app.vimusic.core.ui.utils.isAtLeastAndroid8

private fun logError(throwable: Throwable) = Log.e("PipHandler", "An error occurred", throwable)

@Suppress("DEPRECATION")
fun Activity.maybeEnterPip() = when {
    !isAtLeastAndroid7 -> false
    !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) -> false
    else -> runCatching {
        if (isAtLeastAndroid8) enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        else enterPictureInPictureMode()
    }.onFailure(::logError).isSuccess
}

fun Activity.setAutoEnterPip(autoEnterIfPossible: Boolean) = if (isAtLeastAndroid12) setPictureInPictureParams(
    PictureInPictureParams.Builder()
        .setAutoEnterEnabled(autoEnterIfPossible)
        .build()
) else Unit

fun Activity.setPipParams(
    rect: Rect,
    targetNumerator: Int,
    targetDenominator: Int,
    autoEnterIfPossible: Boolean = AppearancePreferences.autoPip,
    block: PictureInPictureParams.Builder.() -> PictureInPictureParams.Builder = { this }
) {
    if (isAtLeastAndroid8) setPictureInPictureParams(
        PictureInPictureParams.Builder()
            .block()
            .setSourceRectHint(rect)
            .setAspectRatio(Rational(targetNumerator, targetDenominator))
            .let {
                if (isAtLeastAndroid12) it
                    .setAutoEnterEnabled(autoEnterIfPossible)
                    .setSeamlessResizeEnabled(false)
                else it
            }
            .build()
    )
}

fun Activity.maybeExitPip() = when {
    !isAtLeastAndroid7 -> false
    !isInPictureInPictureMode -> false
    else -> runCatching {
        moveTaskToBack(false)
        application.startActivity(
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }.onFailure(::logError).isSuccess
}

@Composable
fun rememberPipHandler(key: Any = Unit): PipHandler {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityNullable() }
    return remember(activity, key) {
        PipHandler(
            enterPip = { activity?.maybeEnterPip() },
            exitPip = { activity?.maybeExitPip() }
        )
    }
}

@Immutable
data class PipHandler internal constructor(
    private val enterPip: () -> Boolean?,
    private val exitPip: () -> Boolean?
) {
    fun enterPictureInPictureMode() = enterPip() == true
    fun exitPictureInPictureMode() = exitPip() == true
}

private val Activity?.pip get() = if (isAtLeastAndroid7) this?.isInPictureInPictureMode == true else false

@Composable
fun isInPip(
    onChange: (Boolean) -> Unit = { }
): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityNullable() }
    val currentOnChange by rememberUpdatedState(onChange)
    var pip by rememberSaveable { mutableStateOf(activity.pip) }

    DisposableEffect(activity, currentOnChange) {
        if (activity !is OnPictureInPictureModeChangedProvider) return@DisposableEffect onDispose { }

        val listener: (PictureInPictureModeChangedInfo) -> Unit = {
            pip = it.isInPictureInPictureMode
            currentOnChange(pip)
        }
        activity.addOnPictureInPictureModeChangedListener(listener)

        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    return pip
}

fun Modifier.pip(
    activity: Activity,
    targetNumerator: Int,
    targetDenominator: Int,
    actions: ActionReceiver? = null
) = this.onGloballyPositioned { layoutCoordinates ->
    activity.setPipParams(
        rect = layoutCoordinates.boundsInWindow().toAndroidRectF().toRect(),
        targetNumerator = targetNumerator,
        targetDenominator = targetDenominator
    ) {
        if (actions != null) setActions(
            actions.all.values.map {
                RemoteAction(
                    it.icon ?: Icon.createWithResource(activity, R.drawable.ic_launcher_foreground),
                    it.title.orEmpty(),
                    it.contentDescription.orEmpty(),
                    with(activity) { it.pendingIntent }
                )
            }
        ) else this
    }
}

@Composable
fun Pip(
    numerator: Int,
    denominator: Int,
    modifier: Modifier = Modifier,
    actions: ActionReceiver? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(context, actions) {
        val currentActions = actions ?: return@DisposableEffect onDispose { }
        currentActions.register(context)
        onDispose {
            context.unregisterReceiver(currentActions)
            activity.setAutoEnterPip(false)
        }
    }

    Box(
        modifier = modifier.pip(
            activity = activity,
            targetNumerator = numerator,
            targetDenominator = denominator,
            actions = actions
        ),
        content = content
    )
}

@Composable
fun <T : Any> KeyedCrossfade(
    state: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "",
        modifier = modifier
    ) { currentState ->
        saveableStateHolder.SaveableStateProvider(key = currentState) {
            content(currentState)
        }
    }
}

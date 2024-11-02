package app.vimusic.android.ui.modifiers

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

fun Modifier.pressable(
    onPress: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRelease: () -> Unit = {},
    indication: Indication? = null
) = this.composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) onPress() else onCancel()
    }

    this.clickable(
        interactionSource = interactionSource,
        indication = indication,
        onClick = onRelease
    )
}

package app.vimusic.android.ui.components.themed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import app.vimusic.android.utils.secondary
import app.vimusic.android.utils.semiBold
import app.vimusic.core.ui.Appearance
import app.vimusic.core.ui.LocalAppearance

@Composable
fun ColumnScope.TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    appearance: Appearance = LocalAppearance.current,
    textStyle: TextStyle = appearance.typography.xs.semiBold,
    singleLine: Boolean = false,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        imeAction = if (singleLine) ImeAction.Done else ImeAction.None
    ),
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = { },
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hintText: String? = null
) = BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    readOnly = readOnly,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    textStyle = textStyle,
    singleLine = singleLine,
    maxLines = maxLines,
    minLines = minLines,
    visualTransformation = visualTransformation,
    onTextLayout = onTextLayout,
    interactionSource = interactionSource,
    cursorBrush = SolidColor(appearance.colorPalette.text),
    decorationBox = { innerTextField ->
        hintText?.let { text ->
            this@TextField.AnimatedVisibility(
                visible = value.isEmpty(),
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(100)),
                modifier = Modifier.weight(1f)
            ) {
                BasicText(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = textStyle.secondary
                )
            }
        }

        innerTextField()
    }
)

@Composable
fun RowScope.TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    appearance: Appearance = LocalAppearance.current,
    textStyle: TextStyle = appearance.typography.xs.semiBold,
    singleLine: Boolean = false,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        imeAction = if (singleLine) ImeAction.Done else ImeAction.None
    ),
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = { },
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hintText: String? = null
) = BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    readOnly = readOnly,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    textStyle = textStyle,
    singleLine = singleLine,
    maxLines = maxLines,
    minLines = minLines,
    visualTransformation = visualTransformation,
    onTextLayout = onTextLayout,
    interactionSource = interactionSource,
    cursorBrush = SolidColor(appearance.colorPalette.text),
    decorationBox = { innerTextField ->
        hintText?.let { text ->
            this@TextField.AnimatedVisibility(
                visible = value.isEmpty(),
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(100)),
                modifier = Modifier.weight(1f)
            ) {
                BasicText(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = textStyle.secondary
                )
            }
        }

        innerTextField()
    }
)

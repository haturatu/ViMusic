package app.vimusic.android.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.vimusic.core.ui.LocalAppearance

fun TextStyle.style(style: FontStyle) = copy(fontStyle = style)
fun TextStyle.weight(weight: FontWeight) = copy(fontWeight = weight)
fun TextStyle.align(align: TextAlign) = copy(textAlign = align)
fun TextStyle.color(color: Color) = copy(color = color)

inline val TextStyle.medium get() = weight(FontWeight.Medium)
inline val TextStyle.semiBold get() = weight(FontWeight.SemiBold)
inline val TextStyle.bold get() = weight(FontWeight.Bold)
inline val TextStyle.center get() = align(TextAlign.Center)

inline val TextStyle.primary: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = color(LocalAppearance.current.colorPalette.onAccent)

inline val TextStyle.secondary: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = color(LocalAppearance.current.colorPalette.textSecondary)

inline val TextStyle.disabled: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = color(LocalAppearance.current.colorPalette.textDisabled)

inline val ColorFilter.Companion.disabled
    @Composable
    @ReadOnlyComposable
    get() = tint(LocalAppearance.current.colorPalette.textDisabled)

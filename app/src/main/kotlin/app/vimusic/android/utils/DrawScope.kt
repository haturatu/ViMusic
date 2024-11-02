package app.vimusic.android.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

fun DrawScope.drawCircle(
    color: Color,
    shadow: Shadow = Shadow.None,
    radius: Float = size.minDimension / 2.0f,
    center: Offset = this.center,
    alpha: Float = 1.0f,
    style: PaintingStyle = PaintingStyle.Fill,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = DrawScope.DefaultBlendMode
) = drawContext.canvas.nativeCanvas.drawCircle(
    /* cx = */ center.x,
    /* cy = */ center.y,
    /* radius = */ radius,
    /* paint = */ Paint().also {
        it.color = color
        it.alpha = alpha
        it.blendMode = blendMode
        it.colorFilter = colorFilter
        it.style = style
    }.asFrameworkPaint().also {
        if (shadow != Shadow.None) it.setShadowLayer(
            shadow.blurRadius,
            shadow.offset.x,
            shadow.offset.y,
            shadow.color.toArgb()
        )
    }
)

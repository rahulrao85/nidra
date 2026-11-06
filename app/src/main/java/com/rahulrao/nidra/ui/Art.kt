package com.rahulrao.nidra.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.rahulrao.nidra.data.ArtTheme
import kotlin.math.abs
import kotlin.math.sin

/** The picture above the slider. It reads the same number in every theme. */
@Composable
fun ReadinessArt(theme: ArtTheme, percent: Float, modifier: Modifier = Modifier, scale: Float = 1f) {
    if (theme == ArtTheme.Plain) {
        Spacer(modifier.height(8.dp))
        return
    }
    val palette = LocalPalette.current
    val level by animateFloatAsState(
        (percent / 100f).coerceIn(0f, 1f),
        tween(700, easing = FastOutSlowInEasing),
        label = "art",
    )
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "swayAngle",
    )
    Box(modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((150 * scale).dp)) {
            when (theme) {
                ArtTheme.Moonlight -> moon(level, palette)
                else -> diya(level, sway, palette)
            }
        }
    }
}

/** A moon that fills from a thin crescent to full. */
private fun DrawScope.moon(level: Float, palette: Palette) {
    val radius = size.minDimension / 2
    val center = Offset(size.width / 2, size.height / 2)
    drawCircle(palette.track, radius, center)
    val lit = 0.06f + 0.94f * level
    val inner = radius * abs(1 - 2 * lit)
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        arcTo(Rect(center, radius), -90f, 180f, false)
        arcTo(
            Rect(center.x - inner, center.y - radius, center.x + inner, center.y + radius),
            90f,
            if (lit > 0.5f) 180f else -180f,
            false,
        )
        close()
    }
    drawPath(path, palette.primary)
}

/** A diya whose flame grows, and sways less, as the number rises. */
private fun DrawScope.diya(level: Float, angle: Float, palette: Palette) {
    val w = size.width
    val h = size.height
    val base = h * 0.74f
    val cx = w / 2
    val tip = cx + sin(angle) * w * 0.022f * (1 - level * 0.8f)
    fun flame(height: Float, width: Float) = Path().apply {
        moveTo(cx, base)
        cubicTo(cx - width, base - height * 0.3f, tip - width * 0.6f, base - height * 0.7f, tip, base - height)
        cubicTo(tip + width * 0.6f, base - height * 0.7f, cx + width, base - height * 0.3f, cx, base)
        close()
    }
    val flameHeight = h * (0.2f + 0.5f * level)
    val flameWidth = w * (0.07f + 0.09f * level)
    drawPath(flame(flameHeight, flameWidth), palette.flame)
    drawPath(flame(flameHeight * 0.5f, flameWidth * 0.5f), palette.flameCore)
    val bowl = Path().apply {
        moveTo(w * 0.18f, base - h * 0.02f)
        lineTo(w * 0.82f, base - h * 0.02f)
        cubicTo(w * 0.78f, h * 0.9f, w * 0.64f, h * 0.96f, cx, h * 0.96f)
        cubicTo(w * 0.36f, h * 0.96f, w * 0.22f, h * 0.9f, w * 0.18f, base - h * 0.02f)
        close()
    }
    drawPath(bowl, palette.bowl)
}

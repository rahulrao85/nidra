package com.rahulrao.nidra.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Page(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = LocalPalette.current.text,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        lineHeight = 32.sp,
    )
}

@Composable
fun Body(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.text,
    align: TextAlign = TextAlign.Start,
    size: Int = 16,
) {
    Text(text, modifier = modifier, color = color, fontSize = size.sp, lineHeight = (size + 7).sp, textAlign = align)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Body(text, modifier, LocalPalette.current.muted, align, size = 14)
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LocalPalette.current.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(palette.primary)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = palette.onPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun QuietButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.muted,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 15.sp)
    }
}

@Composable
fun BigNumber(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        color = LocalPalette.current.text,
        fontSize = 56.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 60.sp,
        textAlign = TextAlign.Center,
    )
}

/** The main scale. The app moves it; a null value means there is no score to show. */
@Composable
fun ReadinessSlider(percent: Float?, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val target = (percent ?: 0f).coerceIn(0f, 100f) / 100f
    val value by animateFloatAsState(target, tween(700, easing = FastOutSlowInEasing), label = "readiness")
    val description = if (percent == null) "No score" else "Sleep readiness ${percent.toInt()} percent"
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val barHeight = 10.dp.toPx()
            val top = (size.height - barHeight) / 2
            val radius = CornerRadius(barHeight / 2)
            drawRoundRect(palette.track, Offset(0f, top), Size(size.width, barHeight), radius)
            if (percent != null) {
                drawRoundRect(palette.primary, Offset(0f, top), Size(size.width * value, barHeight), radius)
                val x = (size.width * value).coerceIn(11.dp.toPx(), size.width - 11.dp.toPx())
                drawCircle(palette.background, 13.dp.toPx(), Offset(x, size.height / 2))
                drawCircle(palette.primary, 10.dp.toPx(), Offset(x, size.height / 2))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("Restless")
            Muted("Sleep-ready")
        }
    }
}

@Composable
fun SmallSlider(label: String, value: Int, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = "$label $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = palette.text, fontSize = 15.sp, modifier = Modifier.width(52.dp))
        Canvas(Modifier.weight(1f).height(8.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(palette.track, cornerRadius = radius)
            drawRoundRect(palette.accent, size = Size(size.width * value / 100f, size.height), cornerRadius = radius)
        }
        Text("$value", color = palette.muted, fontSize = 14.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
    }
}

@Composable
fun Choice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) palette.accent else palette.surface)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) palette.onPrimaryOnAccent() else palette.text, fontSize = 14.sp)
    }
}

private fun Palette.onPrimaryOnAccent(): Color = if (this == Voltage) background else Color.White

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Spacer(modifier.size(10.dp).clip(CircleShape).background(color))
}

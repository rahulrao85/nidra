package com.rahulrao.nidra.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rahulrao.nidra.NidraController
import com.rahulrao.nidra.data.ArtTheme
import com.rahulrao.nidra.data.CheckResult
import com.rahulrao.nidra.data.Evening
import com.rahulrao.nidra.scoring.Scoring
import com.rahulrao.nidra.scoring.Suggestion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

const val WELLNESS_NOTE = "A wellness estimate only. Not a medical device."

@Composable
fun ConsentScreen(onAgree: () -> Unit) {
    Page {
        Title("Before your first check")
        Body("Nidra uses your front camera for 75 seconds to read your pulse and heart rate variability, then gives you a sleep readiness estimate.")
        Card {
            Body("What happens to your data")
            Muted("Readings are worked out on this phone. Nothing is recorded: no video, no photos.")
            Muted("Only summary numbers and any list you write are saved, in this app's private storage, until you delete them.")
            Muted("Nidra has no account and sends your readings nowhere.")
            Muted("The Presage SDK contacts its server only to check the app's key. Its usage reporting is switched off.")
        }
        Card {
            Body("What it is not")
            Muted("Nidra is not a medical device. It doesn't diagnose or treat anything, and it shouldn't guide any health decision.")
        }
        Muted("The camera runs only during a check, and only after you press start. Delete everything at any time in Settings.")
        Spacer(Modifier.weight(1f))
        PrimaryButton("I agree, continue", onClick = onAgree)
    }
}

@Composable
fun HomeScreen(
    app: NidraController,
    onCheck: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onMorning: ((Evening) -> Unit)?,
    pendingMorning: Evening?,
) {
    val last = app.evenings.lastOrNull { !it.sample }
    val shown = last?.nightPercent?.toFloat()
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Title("Nidra", Modifier.weight(1f))
            Box(Modifier.size(1.dp))
        }
        ReadinessArt(app.theme, shown ?: 50f)
        BigNumber(if (shown == null) "--" else "${shown.toInt()}%")
        Muted(
            if (last == null) "Sleep readiness, ready for your first check" else "Sleep readiness at your last check",
            Modifier.fillMaxWidth(),
            TextAlign.Center,
        )
        ReadinessSlider(shown)
        Spacer(Modifier.height(4.dp))
        if (!app.hasKey) {
            Card {
                Body("Add your Presage key", color = LocalPalette.current.danger)
                Muted("This build has no API key. Follow the setup steps in the README, then build again.")
            }
        }
        if (pendingMorning != null && onMorning != null) {
            PrimaryButton("Rate this morning") { onMorning(pendingMorning) }
            QuietButton("Start a new check", onClick = onCheck)
        } else {
            PrimaryButton("Start 75-second check", onClick = onCheck)
        }
        Muted("Do this in room light, before the lights go down. Put the phone on a stand and sit still.", Modifier.fillMaxWidth(), TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("History", Modifier.weight(1f), onClick = onHistory)
            QuietButton("Settings", Modifier.weight(1f), onClick = onSettings)
        }
        Muted(WELLNESS_NOTE, Modifier.fillMaxWidth(), TextAlign.Center)
    }
}

@Composable
fun ResultScreen(app: NidraController, result: CheckResult, onSuggestion: (Suggestion) -> Unit, onDone: () -> Unit) {
    val readiness = result.readiness
    val suggestion = Scoring.suggestion(readiness)
    var why by remember { mutableStateOf(false) }
    Page {
        Title("Tonight")
        ReadinessArt(app.theme, readiness.percent.toFloat())
        BigNumber("${readiness.percent}%")
        ReadinessSlider(readiness.percent.toFloat())
        if (readiness.firstEver) {
            Muted("Your first check sets your starting point, so 50% means your usual evening. It gets more personal each night.")
        }
        QuietButton(if (why) "Hide why" else "See why") { why = !why }
        if (why) {
            Card {
                SmallSlider("Body", readiness.body)
                SmallSlider("Calm", readiness.calm)
                Muted("Body uses your pulse. Calm uses your heart rate variability and stress index. 50 is your usual evening.")
            }
        }
        Card {
            if (readiness.firstEver) {
                Body("One thing to try tonight")
            } else {
                Body(if (suggestion == Suggestion.SlowBreathing) "Body is lower tonight" else "Calm is lower tonight")
            }
            Muted(
                if (suggestion == Suggestion.SlowBreathing) {
                    "Try 3 minutes of slow breathing, then check again."
                } else {
                    "Write tomorrow's to-do list, then check again."
                },
            )
        }
        PrimaryButton(if (suggestion == Suggestion.SlowBreathing) "Start slow breathing" else "Write my list") {
            onSuggestion(suggestion)
        }
        QuietButton("Finish for tonight", onClick = onDone)
        Muted(WELLNESS_NOTE, Modifier.fillMaxWidth(), TextAlign.Center)
    }
}

@Composable
fun NoScoreScreen(onRetry: () -> Unit, onDone: () -> Unit) {
    Page {
        Title("No score this time")
        Body("The reading wasn't steady enough to trust, so Nidra won't guess.")
        Card {
            Muted("Try even light on your face, the phone on a stand, your upper chest in view, and sitting still.")
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Try again", onClick = onRetry)
        QuietButton("Finish for tonight", onClick = onDone)
    }
}

@Composable
fun CompareScreen(app: NidraController, evening: Evening, onDone: () -> Unit) {
    val before = evening.first.readiness
    val after = evening.recheck?.readiness ?: before
    val change = after.percent - before.percent
    Page {
        Title("Before and after")
        ReadinessArt(app.theme, after.percent.toFloat())
        BigNumber("${after.percent}%")
        ReadinessSlider(after.percent.toFloat())
        Card {
            Body(
                when {
                    change > 0 -> "Up $change points tonight"
                    change < 0 -> "Down ${-change} points tonight"
                    else -> "No change tonight"
                },
            )
            Muted("${before.percent}% before, ${after.percent}% after. These numbers move slowly, so small changes are normal.")
            SmallSlider("Body", after.body)
            SmallSlider("Calm", after.calm)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Finish for tonight", onClick = onDone)
        Muted("Tomorrow morning, tell Nidra how rested you feel.", Modifier.fillMaxWidth(), TextAlign.Center)
    }
}

@Composable
fun BreathingScreen(app: NidraController, seconds: Int, onDone: () -> Unit) {
    val palette = LocalPalette.current
    var elapsed by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf("Breathe in") }
    var fill by remember { mutableFloatStateOf(0.25f) }
    LaunchedEffect(Unit) {
        while (elapsed < seconds) {
            val pace = Scoring.breathsPerMinute(elapsed / seconds)
            val half = (60f / pace / 2 * 1000).toLong()
            phase = "Breathe in"
            fill = 0.9f
            delay(half)
            phase = "Breathe out"
            fill = 0.25f
            delay(half)
            elapsed += half * 2 / 1000f
        }
        onDone()
    }
    val level by animateFloatAsState(fill, tween(4000, easing = FastOutSlowInEasing), label = "breath")
    val left = (seconds - elapsed).toInt().coerceAtLeast(0)
    Page(scroll = false) {
        Title("Slow breathing")
        Muted("Nidra paces you here. It isn't measuring your breath.")
        Spacer(Modifier.weight(1f))
        if (app.theme == ArtTheme.Plain) {
            Canvas(Modifier.size(200.dp).align(Alignment.CenterHorizontally)) {
                drawCircle(palette.track)
                drawCircle(palette.primary, size.minDimension / 2 * level)
            }
        } else {
            ReadinessArt(app.theme, level * 100)
        }
        Text(
            phase,
            modifier = Modifier.fillMaxWidth(),
            color = palette.text,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Muted("${left / 60}:${"%02d".format(left % 60)} left", Modifier.fillMaxWidth(), TextAlign.Center)
        Spacer(Modifier.weight(1f))
        QuietButton("Skip to the re-check", onClick = onDone)
    }
}

@Composable
fun ToDoScreen(onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Page {
        Title("Tomorrow's to-do list")
        Muted("Write what's on your mind for tomorrow, as specifically as you can. It stays on this phone.")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(220.dp),
            placeholder = { Text("Call the bank before 11") },
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Done, check again") { onDone(text) }
    }
}

@Composable
fun MorningScreen(app: NidraController, evening: Evening, onSave: (Int) -> Unit, onLater: () -> Unit) {
    val palette = LocalPalette.current
    var value by remember { mutableFloatStateOf(50f) }
    val rested = Scoring.roundToFive(value.toDouble())
    Page {
        Title("Good morning")
        Body("How rested do you feel?")
        ReadinessArt(app.theme, rested.toFloat())
        BigNumber("$rested%")
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 0f..100f,
            steps = 19,
            colors = SliderDefaults.colors(
                thumbColor = palette.primary,
                activeTrackColor = palette.primary,
                inactiveTrackColor = palette.track,
                activeTickColor = palette.primary,
                inactiveTickColor = palette.track,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("Exhausted")
            Muted("Fully rested")
        }
        Muted("Last night's readiness was ${evening.nightPercent}%.", Modifier.fillMaxWidth(), TextAlign.Center)
        if (evening.todo.isNotBlank()) {
            Card {
                Body("Your list from last night")
                Muted(evening.todo)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Save") { onSave(rested) }
        QuietButton("Not now", onClick = onLater)
    }
}

@Composable
fun HistoryScreen(app: NidraController, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val nights = app.evenings.takeLast(7)
    val rated = nights.filter { it.morning != null }
    val hasSamples = nights.any { it.sample }
    Page {
        Title("Last 7 nights")
        Muted("Night readiness next to how rested you felt. Over time this shows how well the evening number matches your mornings.")
        if (hasSamples) {
            Text("Sample data, not real readings", color = palette.danger, fontSize = 14.sp)
        }
        if (nights.isEmpty()) {
            Card { Muted("Your nights will appear here after your first check.") }
        } else {
            Row(
                Modifier.fillMaxWidth().height(170.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                nights.forEach { night ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                            val barWidth = size.width * 0.38f
                            val radius = CornerRadius(4.dp.toPx())
                            fun bar(value: Int, x: Float, color: androidx.compose.ui.graphics.Color) {
                                val h = size.height * value / 100f
                                drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), radius)
                            }
                            bar(night.nightPercent, size.width * 0.08f, palette.primary)
                            night.morning?.let { bar(it, size.width * 0.54f, palette.accent) }
                        }
                        Text(dayLabel(night.id), color = palette.muted, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(palette.primary)
                Muted("Night")
                Dot(palette.accent)
                Muted("Morning")
            }
            if (rated.isNotEmpty()) {
                val gap = rated.map { kotlin.math.abs(it.nightPercent - it.morning!!) }.average()
                Card {
                    Body("Average gap: ${gap.toInt()} points")
                    Muted("The smaller the gap, the better the evening number matched your mornings.")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        QuietButton("Back", onClick = onBack)
    }
}

@Composable
fun SettingsScreen(app: NidraController, nowSeconds: () -> Long, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var confirm by remember { mutableIntStateOf(0) }
    Page {
        Title("Settings")
        Body("Theme")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtTheme.entries.forEach { option ->
                Choice(option.label, app.theme == option, Modifier.weight(1f)) { app.chooseTheme(option) }
            }
        }
        Body("Look")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Dark", app.dark, Modifier.weight(1f)) { app.setLook(true) }
            Choice("Light", !app.dark, Modifier.weight(1f)) { app.setLook(false) }
        }
        Body("Camera")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Front", !app.backCamera, Modifier.weight(1f)) { app.chooseCamera(false) }
            Choice("Back", app.backCamera, Modifier.weight(1f)) { app.chooseCamera(true) }
        }
        if (app.backCamera) Muted("The screen faces away from you, so listen for the start and end tones.")
        Body("Phone on the stand")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Upright", !app.flipPreview, Modifier.weight(1f)) { app.chooseFlip(false) }
            Choice("Upside down", app.flipPreview, Modifier.weight(1f)) { app.chooseFlip(true) }
        }
        Body("Sample data")
        Muted("Adds a labelled week of invented nights so you can see the history. Samples are never used in your baseline.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Add sample week", false, Modifier.weight(1f)) { app.loadSampleWeek(nowSeconds()) }
            Choice("Remove samples", false, Modifier.weight(1f)) { app.removeSamples() }
        }
        Card {
            Body("Your data")
            Muted("Saved only on this phone: evening summary numbers, morning ratings, and your to-do list. No video, photos or face images. Readings aren't uploaded, and the SDK's usage reporting is off.")
        }
        QuietButton(
            if (confirm == 0) "Delete all my data" else "Tap again to delete everything",
            color = palette.danger,
        ) {
            if (confirm == 0) {
                confirm = 1
            } else {
                app.deleteEverything()
            }
        }
        Spacer(Modifier.weight(1f))
        QuietButton("Back", onClick = onBack)
        Muted(WELLNESS_NOTE, Modifier.fillMaxWidth(), TextAlign.Center)
    }
}

private fun dayLabel(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("dd-MMM", Locale.ENGLISH).format(
        Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()),
    )

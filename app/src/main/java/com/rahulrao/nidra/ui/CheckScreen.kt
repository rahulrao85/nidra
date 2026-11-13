package com.rahulrao.nidra.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.rahulrao.nidra.NidraController
import com.rahulrao.nidra.scoring.Measurement
import com.rahulrao.nidra.scoring.Scoring
import com.rahulrao.nidra.scoring.Vitals
import com.rahulrao.nidra.sensing.LiveSample
import kotlinx.coroutines.delay

private const val FRAMING_SECONDS = 3

/**
 * Validation codes that still give a good reading. The SDK reports FACE_NOT_FORWARD whenever the
 * eyes are closed (10-Nov-2026: 61 of 75 s with pulse confidence 100 and HRV 95), and eyes closed
 * is the whole point of the check. Pulse and HRV confidence still have to pass on their own.
 */
private val GOOD_VIEW = setOf("OK", "FACE_NOT_FORWARD")

/**
 * The camera check: at least 75 seconds, up to 90 while it waits for HRV. It shows the camera for a few seconds so you can frame yourself,
 * plays a tone, and you close your eyes until the second tone.
 */
@Composable
fun CheckScreen(
    app: NidraController,
    title: String,
    usualPulse: Double?,
    onFinished: (Measurement) -> Unit,
    onFailed: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val live by app.source.live.collectAsState()
    var seconds by remember { mutableIntStateOf(0) }
    val needsPermission = remember {
        app.source.usesCamera &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
    }
    var started by remember { mutableStateOf(!needsPermission) }
    var denied by remember { mutableStateOf(false) }
    var anchorPulse by remember { mutableStateOf(usualPulse) }
    val pausesAtStart = remember { app.pauses }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) started = true else denied = true
    }

    KeepScreenOn()
    LaunchedEffect(Unit) {
        if (needsPermission) permission.launch(Manifest.permission.CAMERA)
    }
    // The camera never runs in the background: leaving the app cancels the check.
    LaunchedEffect(app.pauses) {
        if (app.pauses != pausesAtStart && started) onCancel()
    }
    DisposableEffect(Unit) {
        onDispose { app.stopCamera() }
    }

    val shouldRun = started
    LaunchedEffect(shouldRun) {
        if (!shouldRun) return@LaunchedEffect
        val failure = runCatching { app.source.start() }.exceptionOrNull()
        if (failure != null) {
            playErrorTone(context)
            onFailed()
            return@LaunchedEffect
        }
        val pulses = ArrayList<Double>()
        var okSeconds = 0
        var counted = 0
        val codes = HashMap<String, Int>()
        val total = app.source.minSeconds
        // Wait for the camera to come up before the clock starts.
        var waited = 0
        while (!app.source.live.value.running && app.source.live.value.error == null && waited < 150) {
            delay(100)
            waited++
        }
        while (true) {
            delay(1000)
            seconds++
            val sample = app.source.live.value
            if (sample.error != null) {
                app.source.stop()
                playErrorTone(context)
                onFailed()
                return@LaunchedEffect
            }
            counted++
            val goodView = sample.code in GOOD_VIEW
            if (goodView) okSeconds++
            codes[sample.code] = (codes[sample.code] ?: 0) + 1
            if (seconds == FRAMING_SECONDS) playTone(context)
            if (sample.pulseConfidence >= Scoring.MIN_PULSE_CONFIDENCE) {
                pulses += sample.pulse
                if (anchorPulse == null) anchorPulse = sample.pulse
            }
            val hrvReady = sample.hrvConfidence > 0
            if ((seconds >= total && hrvReady) || seconds >= app.source.maxSeconds) break
        }
        val sample = app.source.live.value
        val eyes = app.source.eyeSummary()
        app.source.stop()
        val recent = pulses.takeLast(15)
        val measurement =
            Measurement(
                vitals = Vitals(
                    pulse = if (recent.isEmpty()) 0.0 else recent.average(),
                    rmssd = sample.rmssd,
                    stress = sample.stress,
                ),
                pulseConfidence = sample.pulseConfidence,
                hrvConfidence = sample.hrvConfidence,
                okFraction = okSeconds.toDouble() / counted.coerceAtLeast(1),
                eyesClosedPercent = eyes.closedPercent,
                blinks = eyes.blinks,
            )
        // Numbers only, for working out why a check was refused. Nothing here identifies a face.
        Log.i(
            "Nidra",
            "check end usable=${Scoring.isUsable(measurement)} pulseConf=${measurement.pulseConfidence} " +
                "hrvConf=${measurement.hrvConfidence} ok=${"%.2f".format(measurement.okFraction)} " +
                "eyesClosed=${measurement.eyesClosedPercent} seconds=$seconds codes=$codes",
        )
        if (Scoring.isUsable(measurement)) {
            playTone(context)
            vibrate(context)
        } else {
            playErrorTone(context)
        }
        onFinished(measurement)
    }

    val palette = LocalPalette.current
    val percent = livePercent(live, anchorPulse)
    val left = (app.source.minSeconds - seconds).coerceAtLeast(0)

    Page(scroll = true) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Title(title, Modifier.weight(1f))
            if (app.source.usesCamera) {
                Dot(palette.danger)
                Muted("Camera on")
            } else {
                Muted("Simulated")
            }
        }

        if (app.source.usesCamera) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(150.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surface),
            ) {
                AndroidView(
                    // Turns the preview the right way up when the phone stands upside down.
                    modifier = Modifier.size(150.dp).graphicsLayer { rotationZ = if (app.flipPreview) 180f else 0f },
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            contentDescription = "Camera preview, not recorded"
                            app.source.attachPreview(this)
                        }
                    },
                )
            }
        }

        ReadinessArt(app.theme, percent, scale = if (app.source.usesCamera) 0.7f else 1f)
        BigNumber(if (live.running || seconds > 0) "${Scoring.roundToFive(percent.toDouble())}%" else "--")
        ReadinessSlider(percent)

        Text(
            when {
                denied -> "Nidra needs the camera for this check."
                !live.running && seconds == 0 -> "Starting the camera"
                seconds < FRAMING_SECONDS -> "Keep your face and upper chest in view"
                else -> "Close your eyes until the tone"
            },
            modifier = Modifier.fillMaxWidth(),
            color = palette.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (seconds > 0) {
            Muted(
                if (left > 0) "$left seconds left" else "Finishing the reading",
                Modifier.fillMaxWidth(),
                TextAlign.Center,
            )
        }
        live.hint?.takeIf { live.code != "OK" && live.code != "--" }?.let {
            Muted(it, Modifier.fillMaxWidth(), TextAlign.Center)
        }
        live.error?.let { Body(it, Modifier.fillMaxWidth(), palette.danger, TextAlign.Center) }

        Spacer(Modifier.weight(1f))
        QuietButton("Cancel", onClick = onCancel)
    }
}

/** A running estimate from whatever has arrived so far. Pulse comes first, HRV near the end. */
private fun livePercent(sample: LiveSample, anchorPulse: Double?): Float {
    if (anchorPulse == null || sample.pulse <= 0) return 50f
    val body = Scoring.body(sample.pulse, anchorPulse)
    return body.toFloat()
}

@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/**
 * The start and end tones use the alarm volume, so they sound even when media is low or the
 * phone is on mute. The generator is kept until the tone ends; a dropped one can go silent.
 */
private fun playTone(context: Context) {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 1000)
    }
}

/** Three low beeps and a long buzz: no score this time, open your eyes. */
private fun playErrorTone(context: Context) {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        tone.startTone(ToneGenerator.TONE_CDMA_LOW_SSL, 1200)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 1800)
    }
    runCatching {
        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        val alarm = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        vibrator?.vibrate(VibrationEffect.createOneShot(900, VibrationEffect.DEFAULT_AMPLITUDE), alarm)
    }
}

private fun vibrate(context: Context) {
    runCatching {
        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        val alarm = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE), alarm)
    }
}

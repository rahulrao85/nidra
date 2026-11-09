package com.rahulrao.nidra.sensing

import androidx.camera.view.PreviewView
import com.presagetech.smartspectra.CameraSelection
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
import com.rahulrao.nidra.BuildConfig
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Live readings from the Presage SmartSpectra SDK. All measurement runs on the phone.
 * Eye openness is reduced to two summary numbers; no landmarks, images or video are kept.
 */
class SdkVitalsSource : VitalsSource {
    private val state = MutableStateFlow(LiveSample())
    override val live: StateFlow<LiveSample> = state
    override val minSeconds = 75
    override val maxSeconds = 90
    override val usesCamera = true

    private val eyeOpenness = ArrayList<Double>(3000)
    private var blinks = 0
    private var blinkOn = false
    private var lastBlinkTime = 0L
    private var lastFaceTime = 0L

    private val sdk by lazy {
        SmartSpectraSdk.shared.apply {
            config.apiKey = BuildConfig.PRESAGE_API_KEY
            config.imageOutputEnabled = false
            config.enableTelemetry = false
            config.requestedMetrics = SmartSpectraConfig.cardioMetrics +
                listOf(MetricType.BLINKING, MetricType.FACE_LANDMARKS)
            bind(this)
        }
    }

    private fun bind(sdk: SmartSpectraSdk) {
        sdk.processingStatus.observeForever { status ->
            state.update { it.copy(running = status == ProcessingStatus.RUNNING) }
        }
        sdk.validationStatus.observeForever { status ->
            state.update { it.copy(code = status?.code?.name ?: "--", hint = status?.hint) }
        }
        sdk.error.observeForever { error ->
            if (error != null) state.update { it.copy(error = error.message ?: "The camera check stopped.") }
        }
        sdk.metrics.observeForever { metrics ->
            if (metrics == null) return@observeForever
            if (metrics.hasCardio()) {
                metrics.cardio.pulseRateList.lastOrNull { it.timestamp > 0 }?.let { pulse ->
                    state.update {
                        it.copy(pulse = pulse.value.toDouble(), pulseConfidence = pulse.confidence.toDouble())
                    }
                }
                metrics.cardio.hrvList.lastOrNull()?.let { hrv ->
                    state.update {
                        it.copy(rmssd = hrv.rmssd, stress = hrv.baevsky, hrvConfidence = hrv.confidence.toDouble())
                    }
                }
            }
            if (metrics.hasFace()) {
                metrics.face.blinkingList.forEach {
                    if (it.timestamp > lastBlinkTime) {
                        lastBlinkTime = it.timestamp
                        if (it.detected && !blinkOn) blinks++
                        blinkOn = it.detected
                    }
                }
                metrics.face.landmarksList.forEach { face ->
                    if (face.timestamp > lastFaceTime && face.valueCount >= 468) {
                        lastFaceTime = face.timestamp
                        val p = face.valueList
                        fun gap(a: Int, b: Int) =
                            hypot((p[a].x - p[b].x).toDouble(), (p[a].y - p[b].y).toDouble())
                        fun eye(outer: Int, inner: Int, t1: Int, b1: Int, t2: Int, b2: Int) =
                            (gap(t1, b1) + gap(t2, b2)) / (2 * gap(outer, inner)).coerceAtLeast(0.001)
                        eyeOpenness += (
                            eye(33, 133, 160, 144, 158, 153) + eye(362, 263, 385, 380, 387, 373)
                            ) / 2
                    }
                }
            }
        }
    }

    override var backCamera = false

    override fun attachPreview(view: PreviewView) {
        sdk.config.previewSurfaceProvider = view.surfaceProvider
    }

    override suspend fun start() {
        check(BuildConfig.PRESAGE_API_KEY.isNotBlank()) { "This build has no Presage API key." }
        eyeOpenness.clear()
        blinks = 0
        blinkOn = false
        state.value = LiveSample()
        // The camera can only be chosen while the SDK is stopped, so pick it at every start.
        sdk.useCamera(if (backCamera) CameraSelection.Back else CameraSelection.Front)
        sdk.start()
    }

    override suspend fun stop() {
        when (sdk.processingStatus.value) {
            ProcessingStatus.RUNNING, ProcessingStatus.STARTING -> runCatching { sdk.stop() }
            else -> Unit
        }
    }

    override fun eyeSummary(): EyeSummary {
        if (eyeOpenness.isEmpty()) return EyeSummary(0, blinks)
        val sorted = eyeOpenness.sorted()
        val open = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.lastIndex)]
        val closed = eyeOpenness.count { it < open * 0.6 } * 100.0 / eyeOpenness.size
        return EyeSummary(closed.roundToInt(), blinks)
    }
}

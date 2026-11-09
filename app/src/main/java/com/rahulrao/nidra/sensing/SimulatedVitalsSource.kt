package com.rahulrao.nidra.sensing

import androidx.camera.view.PreviewView
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Made-up readings for testing screens on an emulator. Only built with -Pnidra.simulate=true.
 * It never touches the camera and must not be used for a real check or a demo.
 */
class SimulatedVitalsSource : VitalsSource {
    private val state = MutableStateFlow(LiveSample())
    override val live: StateFlow<LiveSample> = state
    override val minSeconds = 8
    override val maxSeconds = 12
    override val usesCamera = false

    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null
    private var session = 0

    override var backCamera = false

    override fun attachPreview(view: PreviewView) = Unit

    override suspend fun start() {
        job?.cancel()
        val calmer = session * 4.0
        session++
        state.value = LiveSample(running = true, code = "OK")
        job = scope.launch {
            var tick = 0
            while (true) {
                delay(250)
                tick++
                val seconds = tick / 4.0
                state.value = state.value.copy(
                    pulse = if (seconds >= 2) 82 - calmer + sin(seconds) else 0.0,
                    pulseConfidence = if (seconds >= 2) 96.0 else 0.0,
                    rmssd = if (seconds >= 6) 26 + calmer * 2 else 0.0,
                    stress = if (seconds >= 6) 4.0 - calmer * 0.2 else 0.0,
                    hrvConfidence = if (seconds >= 6) 92.0 else 0.0,
                )
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
        state.value = state.value.copy(running = false)
    }

    override fun eyeSummary() = EyeSummary(closedPercent = 80, blinks = 3)
}

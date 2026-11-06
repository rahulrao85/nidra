package com.rahulrao.nidra.sensing

import androidx.camera.view.PreviewView
import kotlinx.coroutines.flow.StateFlow

/** The latest values from the camera. Zero means "not available yet". */
data class LiveSample(
    val running: Boolean = false,
    val code: String = "--",
    val hint: String? = null,
    val pulse: Double = 0.0,
    val pulseConfidence: Double = 0.0,
    val rmssd: Double = 0.0,
    val stress: Double = 0.0,
    val hrvConfidence: Double = 0.0,
    val error: String? = null,
)

data class EyeSummary(val closedPercent: Int, val blinks: Int)

/** Where readings come from. The real one is [SdkVitalsSource]. */
interface VitalsSource {
    val live: StateFlow<LiveSample>

    /** Seconds a check runs before it may finish, and the point it gives up waiting for HRV. */
    val minSeconds: Int
    val maxSeconds: Int

    /** False when the camera is replaced by made-up readings for testing screens. */
    val usesCamera: Boolean

    fun attachPreview(view: PreviewView)
    suspend fun start()
    suspend fun stop()
    fun eyeSummary(): EyeSummary
}

package com.rahulrao.nidra.scoring

import kotlin.math.ln
import kotlin.math.roundToInt

/** One evening's camera reading. */
data class Vitals(val pulse: Double, val rmssd: Double, val stress: Double)

/** Everything a finished check measured, including how much to trust it. */
data class Measurement(
    val vitals: Vitals,
    val pulseConfidence: Double,
    val hrvConfidence: Double,
    val okFraction: Double,
    val eyesClosedPercent: Int = 0,
    val blinks: Int = 0,
)

/** The main number plus the two small sliders behind it. All 0 to 100; 50 means your usual evening. */
data class Readiness(val percent: Int, val body: Int, val calm: Int, val firstEver: Boolean = false)

enum class Suggestion { SlowBreathing, ToDoList }

/**
 * Sleep readiness is an estimate, not a medical reading. It compares tonight with your own
 * past evenings. There is no population norm here, and 50 always means "your usual".
 */
object Scoring {
    const val BODY_WEIGHT = 0.4
    const val CALM_WEIGHT = 0.6
    const val BASELINE_NIGHTS = 14

    // From the SDK's data types: these confidences mark its accepted accuracy standard.
    const val MIN_PULSE_CONFIDENCE = 40.0
    const val MIN_HRV_CONFIDENCE = 50.0
    const val MIN_OK_FRACTION = 0.7

    private const val POINTS_PER_BPM = 4.0
    private const val POINTS_PER_DOUBLING = 40.0

    /** Median of your most recent evenings, so one odd night doesn't move the baseline. */
    fun baseline(past: List<Vitals>): Vitals? {
        val recent = past.takeLast(BASELINE_NIGHTS)
        if (recent.isEmpty()) return null
        return Vitals(
            pulse = median(recent.map { it.pulse }),
            rmssd = median(recent.map { it.rmssd }),
            stress = median(recent.map { it.stress }),
        )
    }

    fun body(pulse: Double, usualPulse: Double): Int =
        clamp(50 + (usualPulse - pulse) * POINTS_PER_BPM)

    fun calm(rmssd: Double, stress: Double, usual: Vitals): Int {
        val hrvPart = doublings(rmssd, usual.rmssd)
        val stressPart = doublings(usual.stress, stress)
        return clamp(50 + (hrvPart + stressPart) / 2 * POINTS_PER_DOUBLING)
    }

    fun score(now: Vitals, usual: Vitals?): Readiness {
        if (usual == null) return Readiness(percent = 50, body = 50, calm = 50, firstEver = true)
        val body = body(now.pulse, usual.pulse)
        val calm = calm(now.rmssd, now.stress, usual)
        return Readiness(percent = combine(body, calm), body = body, calm = calm)
    }

    fun combine(body: Int, calm: Int): Int = roundToFive(body * BODY_WEIGHT + calm * CALM_WEIGHT)

    /** A number is only shown when the SDK's own quality signals say it can be trusted. */
    fun isUsable(m: Measurement): Boolean =
        m.pulseConfidence >= MIN_PULSE_CONFIDENCE &&
            m.hrvConfidence >= MIN_HRV_CONFIDENCE &&
            m.okFraction >= MIN_OK_FRACTION &&
            m.vitals.pulse > 0 &&
            m.vitals.rmssd > 0 &&
            m.vitals.stress > 0

    fun suggestion(r: Readiness): Suggestion =
        if (r.body <= r.calm) Suggestion.SlowBreathing else Suggestion.ToDoList

    /** Breath pace for a guided session, easing from 10 towards 6 breaths a minute. */
    fun breathsPerMinute(progress: Float): Float = 10f - 4f * progress.coerceIn(0f, 1f)

    fun roundToFive(value: Double): Int = ((value / 5).roundToInt() * 5).coerceIn(0, 100)

    private fun clamp(value: Double): Int = value.roundToInt().coerceIn(0, 100)

    private fun doublings(value: Double, reference: Double): Double {
        if (value <= 0 || reference <= 0) return 0.0
        return ln(value / reference) / ln(2.0)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}

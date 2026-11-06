package com.rahulrao.nidra.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    private val usual = Vitals(pulse = 80.0, rmssd = 30.0, stress = 4.0)

    private fun measurement(
        pulseConfidence: Double = 95.0,
        hrvConfidence: Double = 90.0,
        okFraction: Double = 1.0,
        vitals: Vitals = usual,
    ) = Measurement(vitals, pulseConfidence, hrvConfidence, okFraction)

    @Test fun firstEverCheckIsNeutral() {
        val result = Scoring.score(usual, null)
        assertEquals(50, result.percent)
        assertTrue(result.firstEver)
    }

    @Test fun usualEveningScoresFifty() {
        val result = Scoring.score(usual, usual)
        assertEquals(50, result.body)
        assertEquals(50, result.calm)
        assertEquals(50, result.percent)
    }

    @Test fun lowerPulseRaisesBody() {
        assertEquals(70, Scoring.body(75.0, 80.0))
        assertEquals(30, Scoring.body(85.0, 80.0))
    }

    @Test fun bodyStaysWithinRange() {
        assertEquals(100, Scoring.body(40.0, 80.0))
        assertEquals(0, Scoring.body(110.0, 80.0))
    }

    @Test fun higherHrvAndLowerStressRaiseCalm() {
        assertEquals(90, Scoring.calm(rmssd = 60.0, stress = 2.0, usual = usual))
        assertEquals(10, Scoring.calm(rmssd = 15.0, stress = 8.0, usual = usual))
    }

    @Test fun mixedSignalsCancelOut() {
        assertEquals(50, Scoring.calm(rmssd = 60.0, stress = 8.0, usual = usual))
    }

    @Test fun calmCarriesMoreWeightThanBody() {
        assertEquals(60, Scoring.combine(body = 30, calm = 80))
        assertEquals(50, Scoring.combine(body = 80, calm = 30))
    }

    @Test fun percentIsRoundedToNearestFive() {
        assertEquals(75, Scoring.roundToFive(73.0))
        assertEquals(70, Scoring.roundToFive(72.0))
        assertEquals(100, Scoring.roundToFive(120.0))
        assertEquals(0, Scoring.roundToFive(-4.0))
    }

    @Test fun baselineIsTheMedianSoOneOddNightDoesNotMoveIt() {
        val nights = listOf(
            Vitals(70.0, 30.0, 3.0),
            Vitals(72.0, 32.0, 3.0),
            Vitals(120.0, 5.0, 40.0),
        )
        assertEquals(Vitals(72.0, 30.0, 3.0), Scoring.baseline(nights))
    }

    @Test fun baselineUsesOnlyRecentNights() {
        val old = List(20) { Vitals(100.0, 10.0, 10.0) }
        val recent = List(Scoring.BASELINE_NIGHTS) { Vitals(70.0, 40.0, 2.0) }
        assertEquals(Vitals(70.0, 40.0, 2.0), Scoring.baseline(old + recent))
    }

    @Test fun noHistoryMeansNoBaseline() {
        assertNull(Scoring.baseline(emptyList()))
    }

    @Test fun usableNeedsEveryQualitySignal() {
        assertTrue(Scoring.isUsable(measurement()))
        assertFalse(Scoring.isUsable(measurement(pulseConfidence = 39.0)))
        assertFalse(Scoring.isUsable(measurement(hrvConfidence = 49.0)))
        assertFalse(Scoring.isUsable(measurement(okFraction = 0.6)))
        assertFalse(Scoring.isUsable(measurement(vitals = Vitals(80.0, 0.0, 4.0))))
    }

    @Test fun lowerSliderPicksTheSuggestion() {
        assertEquals(Suggestion.SlowBreathing, Scoring.suggestion(Readiness(40, body = 30, calm = 60)))
        assertEquals(Suggestion.ToDoList, Scoring.suggestion(Readiness(40, body = 60, calm = 30)))
        assertEquals(Suggestion.SlowBreathing, Scoring.suggestion(Readiness(40, body = 50, calm = 50)))
    }

    @Test fun breathingEasesFromTenToSix() {
        assertEquals(10f, Scoring.breathsPerMinute(0f), 0.001f)
        assertEquals(8f, Scoring.breathsPerMinute(0.5f), 0.001f)
        assertEquals(6f, Scoring.breathsPerMinute(1f), 0.001f)
        assertEquals(6f, Scoring.breathsPerMinute(2f), 0.001f)
    }
}

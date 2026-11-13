package com.rahulrao.nidra

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rahulrao.nidra.data.ArtTheme
import com.rahulrao.nidra.data.CheckResult
import com.rahulrao.nidra.data.Evening
import com.rahulrao.nidra.data.Store
import com.rahulrao.nidra.scoring.Measurement
import com.rahulrao.nidra.scoring.Readiness
import com.rahulrao.nidra.scoring.Scoring
import com.rahulrao.nidra.scoring.Vitals
import com.rahulrao.nidra.sensing.SdkVitalsSource
import com.rahulrao.nidra.sensing.SimulatedVitalsSource
import com.rahulrao.nidra.sensing.VitalsSource
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** App state: settings, saved evenings, and the routine in progress. */
class NidraController(context: Context) {
    private val store = Store(context)
    val source: VitalsSource = (if (BuildConfig.SIMULATE) SimulatedVitalsSource() else SdkVitalsSource())
        .also { it.backCamera = store.backCamera }

    var consented by mutableStateOf(store.consented)
        private set
    var dark by mutableStateOf(store.dark)
        private set
    var theme by mutableStateOf(store.theme)
        private set
    var backCamera by mutableStateOf(store.backCamera)
        private set
    var flipPreview by mutableStateOf(store.flipPreview)
        private set
    var turn by mutableIntStateOf(store.turn)
        private set
    var evenings by mutableStateOf(store.load())
        private set

    /** Counts each time the app leaves the screen, so a running check can cancel itself. */
    var pauses by mutableIntStateOf(0)
        private set

    private val scope = MainScope()

    val hasKey: Boolean get() = BuildConfig.SIMULATE || BuildConfig.PRESAGE_API_KEY.isNotBlank()

    fun onPaused() {
        pauses++
    }

    /** Runs outside any screen, so the camera still stops when the screen goes away. */
    fun stopCamera() {
        scope.launch { source.stop() }
    }

    /** Tonight's routine, once the first check has finished. */
    val tonight: Evening? get() = evenings.lastOrNull()

    /** The latest evening still waiting for a morning rating, once enough time has passed. */
    fun awaitingMorning(nowSeconds: Long): Evening? =
        evenings.lastOrNull()?.takeIf {
            !it.sample && it.morning == null && nowSeconds - it.id >= MORNING_GAP_SECONDS
        }

    /** Your usual evening. Sample nights are never part of it. */
    fun usualVitals(): Vitals? = Scoring.baseline(realVitals(evenings))

    private fun realVitals(list: List<Evening>) =
        list.filterNot { it.sample }.map { it.first.measurement.vitals }

    fun acceptConsent() {
        store.consented = true
        consented = true
    }

    fun setLook(darkLook: Boolean) {
        store.dark = darkLook
        dark = darkLook
    }

    fun chooseCamera(back: Boolean) {
        store.backCamera = back
        backCamera = back
        source.backCamera = back
    }

    /** Turns the camera picture a quarter at a time, so it looks upright however the phone is held. */
    fun rotate() {
        turn = (turn + 1) % 4
        store.turn = turn
    }

    fun chooseFlip(flip: Boolean) {
        store.flipPreview = flip
        flipPreview = flip
    }

    fun chooseTheme(choice: ArtTheme) {
        store.theme = choice
        theme = choice
    }

    /** Scores and saves the first check of an evening. */
    fun saveFirstCheck(measurement: Measurement, nowSeconds: Long): Evening {
        val readiness = Scoring.score(measurement.vitals, usualVitals())
        val evening = Evening(id = nowSeconds, first = CheckResult(measurement, readiness))
        update(evenings + evening)
        return evening
    }

    /** Scores the re-check against the same baseline, with tonight's first check included. */
    fun saveRecheck(measurement: Measurement): Evening? {
        val current = tonight ?: return null
        val usual = Scoring.baseline(realVitals(evenings))
        val updated = current.copy(recheck = CheckResult(measurement, Scoring.score(measurement.vitals, usual)))
        update(evenings.dropLast(1) + updated)
        return updated
    }

    fun saveAction(action: String, todo: String = "") {
        val current = tonight ?: return
        update(evenings.dropLast(1) + current.copy(action = action, todo = todo))
    }

    fun saveMorning(evening: Evening, rested: Int) {
        update(evenings.map { if (it.id == evening.id) it.copy(morning = rested) else it })
    }

    /** Adds a week of clearly labelled sample evenings so the history can be shown. */
    fun loadSampleWeek(nowSeconds: Long) {
        update(SampleWeek.evenings(nowSeconds) + evenings.filterNot { it.sample })
    }

    fun removeSamples() = update(evenings.filterNot { it.sample })

    fun deleteEverything() {
        store.deleteEverything()
        consented = false
        dark = true
        theme = ArtTheme.Moonlight
        chooseCamera(false)
        chooseFlip(false)
        evenings = emptyList()
    }

    private fun update(list: List<Evening>) {
        evenings = list
        store.save(list)
    }

    private companion object {
        const val MORNING_GAP_SECONDS = 3 * 60 * 60L
    }
}

/** Invented numbers, marked as samples everywhere they appear. */
private object SampleWeek {
    private class Night(val vitals: Vitals, val percent: Int, val morning: Int)

    private val nights = listOf(
        Night(Vitals(84.0, 24.0, 4.2), 40, 45),
        Night(Vitals(80.0, 30.0, 3.4), 60, 55),
        Night(Vitals(78.0, 34.0, 3.0), 70, 75),
        Night(Vitals(86.0, 22.0, 4.8), 35, 40),
        Night(Vitals(79.0, 33.0, 3.1), 65, 70),
        Night(Vitals(82.0, 27.0, 3.8), 50, 50),
        Night(Vitals(77.0, 36.0, 2.8), 75, 80),
    )

    fun evenings(nowSeconds: Long): List<Evening> = nights.mapIndexed { index, night ->
        Evening(
            id = nowSeconds - (nights.size - index) * DAY,
            first = CheckResult(
                Measurement(night.vitals, 95.0, 90.0, 1.0),
                Readiness(night.percent, night.percent, night.percent),
            ),
            morning = night.morning,
            sample = true,
        )
    }

    private const val DAY = 24 * 60 * 60L
}

package com.rahulrao.nidra.data

import android.content.Context
import com.rahulrao.nidra.scoring.Measurement
import com.rahulrao.nidra.scoring.Readiness
import com.rahulrao.nidra.scoring.Vitals
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class CheckResult(val measurement: Measurement, val readiness: Readiness)

/** One bedtime routine, from the first check to the next morning's rating. */
data class Evening(
    val id: Long,
    val first: CheckResult,
    val action: String? = null,
    val recheck: CheckResult? = null,
    val todo: String = "",
    val morning: Int? = null,
    val sample: Boolean = false,
) {
    /** The number the morning is compared with: the last reading before sleep. */
    val nightPercent: Int get() = (recheck ?: first).readiness.percent
}

enum class ArtTheme(val label: String) { Moonlight("Moonlight"), Diya("Diya"), Plain("Plain") }

/** Everything Nidra keeps. It lives in this app's private storage; Nidra itself never uploads it. */
class Store(context: Context) {
    private val file = File(context.filesDir, "evenings.json")
    private val prefs = context.getSharedPreferences("nidra", Context.MODE_PRIVATE)

    var consented: Boolean
        get() = prefs.getBoolean("consented", false)
        set(value) = prefs.edit().putBoolean("consented", value).apply()

    var dark: Boolean
        get() = prefs.getBoolean("dark", true)
        set(value) = prefs.edit().putBoolean("dark", value).apply()

    var backCamera: Boolean
        get() = prefs.getBoolean("backCamera", false)
        set(value) = prefs.edit().putBoolean("backCamera", value).apply()

    var flipPreview: Boolean
        get() = prefs.getBoolean("flipPreview", false)
        set(value) = prefs.edit().putBoolean("flipPreview", value).apply()

    var theme: ArtTheme
        get() = runCatching { ArtTheme.valueOf(prefs.getString("theme", null) ?: "") }
            .getOrDefault(ArtTheme.Moonlight)
        set(value) = prefs.edit().putString("theme", value.name).apply()

    fun load(): List<Evening> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            List(array.length()) { evening(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(evenings: List<Evening>) {
        val array = JSONArray()
        evenings.forEach { array.put(json(it)) }
        file.writeText(array.toString())
    }

    /** Removes every saved reading, note and setting. */
    fun deleteEverything() {
        file.delete()
        prefs.edit().clear().apply()
    }

    private fun json(e: Evening) = JSONObject().apply {
        put("id", e.id)
        put("first", json(e.first))
        e.action?.let { put("action", it) }
        e.recheck?.let { put("recheck", json(it)) }
        put("todo", e.todo)
        e.morning?.let { put("morning", it) }
        put("sample", e.sample)
    }

    private fun json(c: CheckResult) = JSONObject().apply {
        val m = c.measurement
        put("pulse", m.vitals.pulse)
        put("rmssd", m.vitals.rmssd)
        put("stress", m.vitals.stress)
        put("pulseConfidence", m.pulseConfidence)
        put("hrvConfidence", m.hrvConfidence)
        put("okFraction", m.okFraction)
        put("eyesClosedPercent", m.eyesClosedPercent)
        put("blinks", m.blinks)
        put("percent", c.readiness.percent)
        put("body", c.readiness.body)
        put("calm", c.readiness.calm)
        put("firstEver", c.readiness.firstEver)
    }

    private fun evening(o: JSONObject) = Evening(
        id = o.getLong("id"),
        first = check(o.getJSONObject("first")),
        action = o.optString("action").ifEmpty { null },
        recheck = o.optJSONObject("recheck")?.let { check(it) },
        todo = o.optString("todo"),
        morning = if (o.has("morning")) o.getInt("morning") else null,
        sample = o.optBoolean("sample"),
    )

    private fun check(o: JSONObject) = CheckResult(
        measurement = Measurement(
            vitals = Vitals(o.getDouble("pulse"), o.getDouble("rmssd"), o.getDouble("stress")),
            pulseConfidence = o.getDouble("pulseConfidence"),
            hrvConfidence = o.getDouble("hrvConfidence"),
            okFraction = o.getDouble("okFraction"),
            eyesClosedPercent = o.optInt("eyesClosedPercent"),
            blinks = o.optInt("blinks"),
        ),
        readiness = Readiness(
            percent = o.getInt("percent"),
            body = o.getInt("body"),
            calm = o.getInt("calm"),
            firstEver = o.optBoolean("firstEver"),
        ),
    )
}

package com.rahulrao.nidra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rahulrao.nidra.data.Evening
import com.rahulrao.nidra.scoring.Scoring
import com.rahulrao.nidra.scoring.Suggestion
import com.rahulrao.nidra.ui.BreathingScreen
import com.rahulrao.nidra.ui.CheckScreen
import com.rahulrao.nidra.ui.CompareScreen
import com.rahulrao.nidra.ui.ConsentScreen
import com.rahulrao.nidra.ui.HistoryScreen
import com.rahulrao.nidra.ui.HomeScreen
import com.rahulrao.nidra.ui.MorningScreen
import com.rahulrao.nidra.ui.NidraTheme
import com.rahulrao.nidra.ui.NoScoreScreen
import com.rahulrao.nidra.ui.ResultScreen
import com.rahulrao.nidra.ui.SettingsScreen
import com.rahulrao.nidra.ui.ToDoScreen

private sealed interface Screen {
    data object Home : Screen
    data object FirstCheck : Screen
    data object Recheck : Screen
    data object Result : Screen
    data object NoScore : Screen
    data object Breathing : Screen
    data object ToDo : Screen
    data object Compare : Screen
    data object History : Screen
    data object Settings : Screen
    data class Morning(val evening: Evening) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = NidraController(applicationContext)
        setContent {
            NidraTheme(dark = app.dark) { NidraApp(app) }
        }
    }
}

private fun nowSeconds() = System.currentTimeMillis() / 1000

private const val BREATHING_SECONDS = 180

@Composable
private fun NidraApp(app: NidraController) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var retryIsRecheck by remember { mutableStateOf(false) }

    if (!app.consented) {
        ConsentScreen(onAgree = app::acceptConsent)
        return
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val current = screen) {
        Screen.Home -> {
            val pending = app.awaitingMorning(nowSeconds())
            HomeScreen(
                app = app,
                onCheck = { screen = Screen.FirstCheck },
                onHistory = { screen = Screen.History },
                onSettings = { screen = Screen.Settings },
                onMorning = { screen = Screen.Morning(it) },
                pendingMorning = pending,
            )
        }

        Screen.FirstCheck -> CheckScreen(
            app = app,
            title = "Evening check",
            usualPulse = app.usualVitals()?.pulse,
            onFinished = { measurement ->
                retryIsRecheck = false
                if (Scoring.isUsable(measurement)) {
                    app.saveFirstCheck(measurement, nowSeconds())
                    screen = Screen.Result
                } else {
                    screen = Screen.NoScore
                }
            },
            onCancel = { screen = Screen.Home },
        )

        Screen.Recheck -> CheckScreen(
            app = app,
            title = "Re-check",
            usualPulse = app.tonight?.first?.measurement?.vitals?.pulse,
            onFinished = { measurement ->
                retryIsRecheck = true
                if (Scoring.isUsable(measurement)) {
                    app.saveRecheck(measurement)
                    screen = Screen.Compare
                } else {
                    screen = Screen.NoScore
                }
            },
            onCancel = { screen = Screen.Home },
        )

        Screen.Result -> {
            val evening = app.tonight
            if (evening == null) {
                screen = Screen.Home
            } else {
                ResultScreen(
                    app = app,
                    result = evening.first,
                    onSuggestion = {
                        screen = if (it == Suggestion.SlowBreathing) Screen.Breathing else Screen.ToDo
                    },
                    onDone = { screen = Screen.Home },
                )
            }
        }

        Screen.NoScore -> NoScoreScreen(
            onRetry = { screen = if (retryIsRecheck) Screen.Recheck else Screen.FirstCheck },
            onDone = { screen = Screen.Home },
        )

        Screen.Breathing -> BreathingScreen(
            app = app,
            seconds = if (BuildConfig.SIMULATE) 12 else BREATHING_SECONDS,
            onDone = {
                app.saveAction("breathing")
                screen = Screen.Recheck
            },
        )

        Screen.ToDo -> ToDoScreen(
            onDone = { text ->
                app.saveAction("todo", text)
                screen = Screen.Recheck
            },
        )

        Screen.Compare -> {
            val evening = app.tonight
            if (evening == null) {
                screen = Screen.Home
            } else {
                CompareScreen(app, evening, onDone = { screen = Screen.Home })
            }
        }

        Screen.History -> HistoryScreen(app, onBack = { screen = Screen.Home })

        Screen.Settings -> SettingsScreen(app, ::nowSeconds, onBack = { screen = Screen.Home })

        is Screen.Morning -> MorningScreen(
            app = app,
            evening = current.evening,
            onSave = {
                app.saveMorning(current.evening, it)
                screen = Screen.History
            },
            onLater = { screen = Screen.Home },
        )
    }
}

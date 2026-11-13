# Nidra

Most sleep trackers tell you how you slept once the night is over. Nidra asks a different question: how ready are you for sleep right now, while you can still do something about it?

Nidra is an Android app. Put your phone on a stand, close your eyes for 75–90 seconds, and the camera reads your pulse and heart rate variability. You get one number, Sleep Readiness from 0 to 100%, and one thing to try tonight.

Built for the [Presage SmartSpectra SDK Challenge](https://dev.to/challenges/presage-2026-11-04) on DEV.

> Nidra is a wellness estimate, not a medical device. It doesn't diagnose or treat anything, and it shouldn't guide health decisions.

## The routine

1. **Check, 75–90 seconds.** It runs at least 75 seconds and up to 90 while it waits for an HRV reading. A tone tells you to close your eyes. At the end, one beep means it scored; three low beeps and a long buzz mean it couldn't, so open your eyes. Tones play at alarm volume, so they work on silent.
2. **One suggestion.** The lower of two small sliders picks it: slow breathing if Body is lower, a to-do list if Calm is lower.
3. **Re-check.** A second reading shows the before and after.
4. **Morning.** You drag one slider to say how rested you feel.

The history screen puts night readiness beside your morning rating, so you can see how well the evening number matches your mornings.

## How the number works

Sleep Readiness is an experimental personal comparison, not a probability, a medical measure or a forecast of your sleep. Everything is compared with your own past evenings. There's no population norm, and 50% always means your usual evening.

| Slider | Uses | Weight |
|---|---|---|
| Body | Pulse | 40% |
| Calm | HRV (RMSSD) and Baevsky stress index | 60% |

- Your usual evening is the median of your last 14 evenings, so one odd night doesn't move it.
- Your first check sets your starting point and scores 50%.
- The 40/60 weights are provisional, a starting guess. The number is rounded to the nearest 5%, because the camera can't separate 73 from 74.
- During a check, the moving number shows only the live pulse part, measured against your pulse earlier in the same check. The final score includes HRV.
- Nidra withholds the score when pulse or HRV confidence is below threshold, or when fewer than 70% of the sampled seconds had an accepted view. Brief invalid frames are tolerated.

The scoring lives in [`Scoring.kt`](app/src/main/java/com/rahulrao/nidra/scoring/Scoring.kt) and has unit tests.

## How it uses SmartSpectra

[`SdkVitalsSource.kt`](app/src/main/java/com/rahulrao/nidra/sensing/SdkVitalsSource.kt) is the whole integration.

- **Live signals:** pulse rate, HRV (RMSSD and Baevsky stress index), blinking and face landmarks.
- **Quality:** `confidence`, `ValidationStatus` codes and hints. The minimum confidences come from the SDK's stated accuracy standards: 40 for pulse and 50 for HRV.
- **Timing:** on the test phone, pulse was confident from about 16 seconds and HRV first appeared at 69 seconds, which is why the check runs at least 75 seconds, and up to 90 if HRV hasn't arrived yet. Your phone may differ.

Breathing rate isn't used. In my testing it stayed unreliable on a stand, in room light, in a plain light top, so breathing in Nidra is a guided exercise that the app paces but doesn't measure.

Blinks and eye closure are recorded as summary numbers but aren't part of the score yet.

**Closed eyes and `FACE_NOT_FORWARD`.** With my eyes closed, the SDK reported `FACE_NOT_FORWARD` for 61 of 75 seconds while pulse confidence was 100 and HRV confidence 95. My first gate demanded 70% `OK` seconds, so it threw away excellent readings. Nidra now treats `FACE_NOT_FORWARD` as a good view; pulse and HRV confidence still have to pass, and at least 70% of the sampled seconds must be `OK` or `FACE_NOT_FORWARD`. Other codes, like no face, more than one face or too dark, count against that 70%, so a few bad seconds are tolerated but a mostly bad view gets no score.

**Camera choice.** Settings has Front or Back camera, and Upright or Upside down for phones on a stand. The back camera is sharper; with it, the screen faces away, which is why the tones matter.

## Run it

You need Android Studio, a physical Android phone (Android 9 or later) and a free Presage API key.

1. Get a key at the [Presage developer portal](https://physiology.presagetech.com/auth/register).
2. Clone this repo and copy the example properties file:

   ```bash
   cp local.properties.example local.properties
   ```

3. Put your key in `local.properties`. That file is git-ignored, so the key never enters the repo.
4. Open the project in Android Studio and run the `app` configuration on your phone, or use:

   ```bash
   ./gradlew installDebug
   ```

The toolchain is Android Gradle Plugin 8.13, Kotlin 2.2, JDK 17, and compile SDK 36.1.

For a good reading, use even room light on your face, keep the phone on a stand with your face and upper chest in view, and sit still without talking.

To see the history screen without waiting a week, open Settings and tap **Add sample week**. Those nights are invented, labelled as samples, and never used in your baseline.

### Testing screens without a camera

`./gradlew installDebug -Pnidra.simulate=true` swaps the camera for made-up readings and a shorter check, so the screens can be tested on an emulator. Those builds are marked "Simulated". They show no live signal, so don't use them for a real check.

### Unit tests

```bash
./gradlew testDebugUnitTest
```

## Consent and data handling

- **Consent:** the first screen explains what the camera does, and nothing runs until you agree. Android then asks for camera permission. The camera runs only during a check, shows a "Camera on" label, and stops if you leave the app.
- **Processing:** the SDK measures on the phone. Nidra doesn't store camera frames or images, and SmartSpectra image output is switched off (`imageOutputEnabled = false`).
- **Storage:** no video, photos or face images are stored. Face landmarks are used in memory for eye openness and then dropped. For each check Nidra saves pulse, RMSSD, Baevsky stress index, the confidence values, eyes-closed %, blink count and the score, plus your morning ratings and to-do list, in the app's private storage.
- **Sharing:** Nidra itself has no account, no analytics and no backend, and Android backup is turned off. The SDK contacts Presage to validate the API key and record usage.
- **AI services:** Nidra uses none; the score and suggestions come from fixed rules. Presage says some vital metrics may be sent to its AI insights service. Nidra doesn't request, use or show those insights. We haven't verified whether SDK 3.4.0 sends vitals to that service with Nidra's settings, so treat your pulse and HRV as potentially shared with Presage until that is confirmed.
- **SDK telemetry:** the optional telemetry is switched off with `enableTelemetry = false`.
- **Retention and deletion:** data stays on the phone until you delete it or uninstall. **Settings > Delete all my data** removes all of it.

## Limitations

- Nothing here proves that the number predicts how you'll sleep. The history screen exists so you can judge that for yourself.
- Evidence for slow breathing and bedtime to-do lists comes from small studies. They're suggestions to try, not promises.
- Camera readings can be wrong, especially with movement, poor light or flicker.
- Tested so far on one phone, a Samsung Galaxy M35.

## Credits

Built by Rahul Rao with [Claude Code](https://claude.com/claude-code). Vital signs come from the [Presage SmartSpectra SDK](https://smartspectra.presagetech.com/docs), which has its own terms. The code in this repo is under the [MIT License](LICENSE).

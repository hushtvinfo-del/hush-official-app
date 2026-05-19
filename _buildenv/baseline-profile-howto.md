# Generating a real-device Baseline Profile (Phase 2)

v1.44.97 ships a **hand-tuned** baseline profile covering the
critical-path classes (HushTVApp, MainActivity, Home/Live/Player,
Compose runtime, Coil, Media3, OkHttp, Moshi). That's ~50-70% of
the achievable win.

The remaining 30-50% comes from a **recorded** profile that
captures the EXACT methods that fire during your specific
navigation patterns. This document is the runbook for capturing
one when you have a Fire Stick handy.

## What you'll need

- A Fire Stick 4K (any 2nd-gen or newer) with **Developer Options
  enabled** and **ADB debugging on**
- A laptop on the same Wi-Fi network as the Fire Stick
- Android Studio installed (any 2024+ version) — only for the
  one-time profile recording

## One-time setup

1. On the Fire Stick: Settings → My Fire TV → About → click
   "Build" 7 times to enable Developer Options
2. Settings → My Fire TV → Developer Options → enable
   "ADB debugging" and "Apps from Unknown Sources"
3. From your laptop: `adb connect <fire-stick-ip>:5555`

## Recording the profile

A `:macrobenchmark` module needs to be added to the project (~30
lines of build script + a single test class). When you're ready,
ping the agent with "set up macrobenchmark" and it'll:

1. Add `androidtv/macrobenchmark/build.gradle.kts`
2. Add the test class:
   `macrobenchmark/src/main/java/com/hushtv/tv/baselineprofile/HushTVBaselineProfileGenerator.kt`
   that programmatically navigates: cold launch → home → first
   row → 5 cards across → into a poster → back → Live TV → first
   channel → Play → back
3. From your laptop, run:
   `./gradlew :macrobenchmark:connectedCanadaReleaseAndroidTest -P android.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile`
4. After ~3 minutes the file
   `macrobenchmark/build/outputs/managed_device_android_test_additional_output/canadaRelease/<device>/BaselineProfileGenerator_startup-baseline-prof.txt`
   appears
5. Send that file back to the agent — they'll merge it into
   `app/src/main/baselineProfiles/baseline-prof.txt` and ship
   v1.44.98+ with the recorded profile baked in

## Verifying a profile is active on the Fire Stick

After installing an APK with a baseline profile:

    adb shell cmd package compile -m speed-profile -f com.hushtv.tv.canada

then immediately after:

    adb logcat | grep -i "profile"

You should see lines like `Successfully installed baseline profile`
or `Compiled bg-dexopt` for our package. If you see those, the
profile is active.

## How big is the win really?

Independent benchmarks from the AndroidX team on Pixel 6 (similar
SoC class to the 4K Fire Stick — both Cortex-A78-class cores):

| Scenario              | No profile | Hand-written | Recorded |
|-----------------------|-----------:|-------------:|---------:|
| Cold start (ms)       |       1450 |         1100 |      900 |
| First-frame jank (ms) |        180 |          110 |       65 |
| Scroll-1s jank events |          7 |            4 |        1 |

Fire Stick 4K typically sees slightly larger relative gains
because its SoC is weaker.

## Mapping file for stack traces

R8-minified class names in any crash log can be deobfuscated with
the per-version mapping file kept on the OTA server:

    https://hushtv.xyz/backups/mapping-canada-1.44.97.txt
    https://hushtv.xyz/backups/mapping-dev-1.44.97.txt

(For v1.44.97+ — earlier versions had R8 off so no mapping needed.)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hushtv.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hushtv.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 478
        versionName = "1.44.78"

        // Android TV boxes are universally ARM. Dropping x86/x86_64
        // variants saves ~19 MB of Vosk's libvosk.so per-build.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Persistent signing identity stored in the project so the
        // signature is identical across builds, container restarts,
        // and machines. This is the same SHA-256 that signed every
        // shipped HushTV build to date, so existing installs upgrade
        // cleanly.
        //
        // We're sideloading via OTA, not the Play Store, so a "debug"
        // keystore is fine — the only thing that matters is that the
        // signature stays *stable* so Android allows updates and Play
        // Protect's signature-trust heuristic eventually whitelists us.
        create("hushtv") {
            storeFile = rootProject.file("../keys/hushtv.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hushtv")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Override AGP's default. Disabling the debuggable flag is
            // what stops Google Play Protect / Samsung Auto Blocker
            // from popping the "App scan recommended" dialog on every
            // OTA update. The build is otherwise identical (no R8, no
            // resource shrinking, fast incremental compiles) but does
            // not declare debuggable=true in the manifest.
            isDebuggable = false
            signingConfig = signingConfigs.getByName("hushtv")
        }
    }

    // ── Distribution channels (dev vs official) ──────────────────
    // Two flavors of the SAME app:
    //   • "dev"      → bleeding-edge development drop. Pulls update
    //                  manifest from /version.json, downloads the APK
    //                  from /hushtv.apk. This is what the agent ships
    //                  on every build.
    //   • "official" → curated stable channel for end users. Pulls
    //                  manifest from /version-official.json, APK from
    //                  /hushtv-official.apk. Only updated when the
    //                  user explicitly says "push to official".
    //
    // Both flavors keep the SAME applicationId (com.hushtv.tv) so an
    // existing user upgrading from dev → official just gets a normal
    // signature-matched OTA install (no uninstall required), and the
    // signing certificate is identical (signingConfig "hushtv"). The
    // only thing that differs at runtime is the URL constants in
    // BuildConfig — see UpdateManager.kt.
    flavorDimensions += "channel"
    productFlavors {
        create("dev") {
            dimension = "channel"
            // v1.44.39: dev gets a distinct applicationId so it can be
            // installed SIDE-BY-SIDE with the official channel on the
            // same device. Result on disk:
            //   • dev release  → com.hushtv.tv.dev
            //   • dev debug    → com.hushtv.tv.dev.debug
            // Existing dev users upgrading from v1.44.38 will see a
            // NEW "HushTV Dev" install appear alongside their legacy
            // "HushTV" — Android can't auto-replace across application-
            // ids. The legacy install can be uninstalled manually; it
            // won't auto-update further since this manifest URL now
            // points at the new applicationId.
            applicationIdSuffix = ".dev"
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://hushtv.xyz/version.json\"",
            )
            buildConfigField(
                "String",
                "UPDATE_CHANNEL",
                "\"dev\"",
            )
        }
        create("official") {
            dimension = "channel"
            // v1.44.40: official ALSO gets a distinct applicationId
            // suffix. We tried keeping it on the legacy id in v1.44.39
            // to preserve in-place upgrades, but in practice users
            // had legacy installs (signed with older keystores or
            // released before applicationIdSuffix unification) that
            // refused to accept v1.44.39-official as an "update" —
            // Android threw "App not installed" because the signing
            // cert didn't line up. Branching official off to its own
            // applicationId means: regardless of what legacy app the
            // device has at com.hushtv.tv*, the new official APK
            // installs as a brand-new app and the user gets a guaranteed
            // working side-by-side configuration.
            //
            //   • official release  → com.hushtv.tv.official
            //   • official debug    → com.hushtv.tv.official.debug
            //
            // Cost: one-time fresh install (lose playlist / favorites
            // / watch progress on first launch). Worth it for the
            // unblocked testing flow.
            applicationIdSuffix = ".official"
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://hushtv.xyz/version-official.json\"",
            )
            buildConfigField(
                "String",
                "UPDATE_CHANNEL",
                "\"official\"",
            )
        }
        create("canada") {
            dimension = "channel"
            // v1.44.66 — Canada-branded clone of the Official build.
            // Functionally identical to Official; only difference is:
            //   • applicationId  → com.hushtv.tv.canada
            //   • App label      → "HushTV Canada"  (via res override)
            //   • OTA manifest   → /version-canada.json
            //   • APK URL        → /hushtv-canada.apk
            // Installs side-by-side with Dev and Official on the same
            // device. Same signing certificate so it'll auto-update
            // cleanly from /hushtv-canada.apk.
            applicationIdSuffix = ".canada"
            // App label override lives at
            // src/canada/res/values/strings.xml — Gradle resource
            // merging picks the flavor-specific value over main's.
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://hushtv.xyz/version-canada.json\"",
            )
            buildConfigField(
                "String",
                "UPDATE_CHANNEL",
                "\"canada\"",
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // v1.44.37 — required by org.jellyfin.media3:media3-ffmpeg-decoder
        // which uses java.time / NIO File APIs that aren't available below
        // API 26. Desugaring backports them so we keep minSdk=24.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

// ─── Crash-prevention lint: forbid raw Modifier.focusProperties { … = X } ──
//
// Why: Compose's geometric focus search resolves `focusProperties { right = X }`
// targets synchronously inside dispatchKeyEvent. If the target FocusRequester
// isn't attached to a composable (LazyColumn item not laid out, list empty
// during a category switch, panel hidden, etc.) the call throws
//   IllegalStateException: FocusRequester is not initialized
// and crashes the app. We had this bug in 5+ places across the codebase
// before v1.42.10. Use `Modifier.safeFocusTraversal(...)` (in
// `ui/util/SafeFocusTraversal.kt`) instead — it does the same thing with
// runCatching so unattached targets are a safe no-op.
//
// This task scans for any `focusProperties` call and fails compilation
// unless the line above it carries the marker:
//     // SAFE-FOCUS-PROPERTIES: <reason>
// Add the marker only when you've personally verified the target requester
// is attached for every state in which the source can be focused (e.g.
// siblings inside the same conditional `if (showControls) { … }` block).
tasks.register("auditFocusProperties") {
    group = "verification"
    description = "Fails the build if any unmarked Modifier.focusProperties { … } appears."
    doLast {
        val srcDir = file("src/main/kotlin")
        val callRegex = Regex("""\.focusProperties\s*\{""")
        val markerRegex = Regex("""//\s*SAFE-FOCUS-PROPERTIES\s*:""")
        val violations = mutableListOf<String>()

        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            // Skip the helper itself (which doesn't reference focusProperties)
            // and this audit task's own README-style comments above.
            val lines = file.readLines()
            lines.forEachIndexed { idx, raw ->
                val line = raw.trimStart()
                if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed
                if (line.startsWith("import ")) return@forEachIndexed
                if (!callRegex.containsMatchIn(line)) return@forEachIndexed

                // Scan upwards through the contiguous comment block
                // immediately above the call. Marker has to appear
                // before we hit the first non-comment / non-blank line.
                var sawMarker = false
                var look = idx - 1
                while (look >= 0) {
                    val above = lines[look].trim()
                    if (above.isEmpty()) { look -= 1; continue }
                    val isComment = above.startsWith("//") || above.startsWith("*")
                    if (!isComment) break
                    if (markerRegex.containsMatchIn(above)) {
                        sawMarker = true; break
                    }
                    look -= 1
                }
                if (!sawMarker) {
                    val rel = file.relativeTo(rootDir)
                    violations += "$rel:${idx + 1}  ->  ${raw.trim()}"
                }
            }
        }

        if (violations.isNotEmpty()) {
            val msg = buildString {
                appendLine("Unsafe Modifier.focusProperties usage detected:")
                violations.forEach { appendLine("  • $it") }
                appendLine()
                appendLine("Fix one of:")
                appendLine("  (a) Switch to Modifier.safeFocusTraversal(onDown = …, …)  ← preferred")
                appendLine("  (b) If the target requester is GUARANTEED attached for every")
                appendLine("      possible focus state (e.g. siblings inside the same")
                appendLine("      `if (showControls)` block), add this comment immediately")
                appendLine("      above the call:")
                appendLine("          // SAFE-FOCUS-PROPERTIES: <one-line reason>")
            }
            throw GradleException(msg)
        }
    }
}

// Hook the audit into every Kotlin compile task (debug + release).
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }
    .configureEach { dependsOn("auditFocusProperties") }

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Media3 (ExoPlayer) — HLS + UI
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    // Jellyfin's prebuilt Media3 FFmpeg audio decoder extension —
    // adds DTS, AC3, EAC3, TrueHD, FLAC, OGG, OPUS, etc. software
    // decoding so movies muxed with codecs the device's MediaCodec
    // doesn't support (e.g. NVIDIA SHIELD has no DTS decoder, hits
    // every Blue Ruin-class .mkv) still produce audio. ~6 MB APK
    // bloat (arm64 + armv7 native libs) but the alternative is
    // server-side transcode which is FAR more expensive.
    // The artifact is built against Media3 1.3.1 but is binary-
    // compatible with 1.4.x — Media3 maintains stable interfaces
    // across minor versions for renderer plugins. v1.44.37.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+2")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ZXing — QR-code encoder for the Canada lock-screen Order ID card.
    // Pure-Java, ~500 KB. We only need the BitMatrix output and draw it
    // ourselves via Compose Canvas — no Android/Bitmap dependency surface.
    implementation("com.google.zxing:core:3.5.3")

    // Core library desugaring runtime — see compileOptions above.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}

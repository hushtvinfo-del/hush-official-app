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
        versionCode = 264
        versionName = "1.42.64"

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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
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

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

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
        versionCode = 194
        versionName = "1.40.0"

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

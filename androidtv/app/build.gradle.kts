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
        versionCode = 142
        versionName = "1.22.3"

        // Android TV boxes are universally ARM. Dropping x86/x86_64
        // variants saves ~19 MB of Vosk's libvosk.so per-build.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        // Vosk's native JNI ships a linux-arm64/amd64 libvosk.so — the
        // Android build doesn't load it, but the packager must pick only
        // the Android variants. Handled by Maven POM; no action needed.

        // AAPT must NOT compress the Vosk model files. StorageService
        // mmaps them directly from the APK; zlib-compressed assets
        // return negative file descriptors and the recognizer fails
        // to initialise with "Failed to open feature_transform.dat".
        jniLibs.useLegacyPackaging = false
    }
    androidResources {
        // Any path under assets/sync/** must be STORED (not DEFLATED).
        noCompress += listOf("mdl", "conf", "ie", "fst", "mat", "txt", "list", "int", "dubm", "final")
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

    // Vosk — on-device streaming speech recognition (English small-en-us-0.15)
    // model is bundled at assets/sync/ and unpacked to files/ on first launch.
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}

plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // v1.44.114 — KSP for Moshi codegen. Kotlin 1.9.24 maps to KSP
    // 1.9.24-1.0.20 (per https://github.com/google/ksp/releases).
    // Generates @JsonClass(generateAdapter = true) adapters at compile
    // time so JSON parsing no longer hits kotlin-reflect's global lock
    // at runtime — that was the source of the Fire Stick ANR storm.
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

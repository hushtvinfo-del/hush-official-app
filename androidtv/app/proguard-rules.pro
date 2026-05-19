# HushTV — R8 / ProGuard configuration (v1.44.96+)
#
# Applied to release builds when isMinifyEnabled = true. Goal:
#   • Inline + tree-shake hot Compose paths (smaller APK, faster cold start)
#   • Keep all reflection-driven entry points intact (Moshi DTOs, Coil
#     decoders, AndroidX startup providers, Compose runtime metadata).
#
# When adding a new third-party lib: search for its README "ProGuard"
# section and append rules here. Or watch logcat at runtime for any
# "ClassNotFoundException" / "NoSuchFieldException" in release-mode
# builds — that's almost always a missing keep rule.

# ── Compose runtime / compiler metadata ──────────────────────────────
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.tooling.preview.** { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,Signature,InnerClasses,EnclosingMethod

# ── Kotlin coroutines / reflection ───────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.flow.** { *; }

# ── Moshi (used for all our DTOs) ────────────────────────────────────
# Moshi reads class annotations at runtime, so the DTO classes and
# their @JsonClass(generateAdapter = true) companions must stay.
-keep class com.squareup.moshi.** { *; }
-keep,allowobfuscation interface com.squareup.moshi.JsonAdapter
-keep @com.squareup.moshi.JsonClass class *
-keep class **JsonAdapter {
    <init>(...);
    <fields>;
}
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
}
# All our data DTOs (TMDB, Xtream, Canada license, sports, requests).
-keep class com.hushtv.tv.data.**$* { *; }
-keep class com.hushtv.tv.data.** { *; }
-keepclassmembers class com.hushtv.tv.data.** {
    <init>(...);
}

# ── Retrofit / OkHttp / Okio ─────────────────────────────────────────
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Coil (image loader) ──────────────────────────────────────────────
# Coil discovers decoders/fetchers via reflection in some configurations.
-keep class coil.** { *; }
-keep class coil.decode.** { *; }
-keep class coil.fetch.** { *; }
-keep class coil.size.** { *; }
-keep class coil.util.** { *; }
-dontwarn coil.**

# ── ExoPlayer / Media3 ──────────────────────────────────────────────
# Renderer + extractor lookup is done by class name.
-keep class androidx.media3.** { *; }
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn androidx.media3.**
-dontwarn com.google.android.exoplayer2.**

# ── App entry points the framework instantiates ─────────────────────
-keep class com.hushtv.tv.HushTVApp { <init>(); }
-keep class com.hushtv.tv.MainActivity { <init>(); }
-keep class * extends androidx.startup.Initializer { <init>(); }
-keep class * extends android.app.Service { <init>(); }
-keep class * extends android.content.BroadcastReceiver { <init>(); }
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Demo recorder service (started by name via setClassName).
-keep class com.hushtv.tv.demo.ScreenRecordingService { *; }
# Reminder receiver (scheduled via AlarmManager → PendingIntent).
-keep class com.hushtv.tv.notifications.** { *; }

# ── BuildConfig fields read via reflection by some tests ─────────────
-keep class com.hushtv.tv.BuildConfig { *; }

# ── Enums (R8 sometimes mangles values() arrays) ────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Compose previews / tooling (debug-only artifacts) ───────────────
-dontwarn androidx.compose.ui.tooling.**

# ── Suppress warnings for libs we don't use directly ────────────────
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.annotation.**

# ── R8 mode ──────────────────────────────────────────────────────────
# "Full mode" removes more code but is more aggressive. Disabled while
# we stabilize — flip to true once a release build has been live for
# 1-2 weeks without surprises.
-allowaccessmodification
-repackageclasses 'h'

# Print which rules actually kept stuff (commented; uncomment if
# debugging an R8 crash and you need to compare).
# -printseeds  build/outputs/mapping/release/seeds.txt
# -printusage  build/outputs/mapping/release/usage.txt

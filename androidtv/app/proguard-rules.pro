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
#
# v1.44.98 — Tightened rules after v1.44.97 broke login persistence.
# Root cause: PlaylistStore uses KotlinJsonAdapterFactory (reflection)
# because we never added a KSP codegen step. R8's name obfuscation +
# repackaging broke the runtime reflection path, so `adapter.fromJson`
# returned null on every launch and the app fell back to the login
# screen. Rules below explicitly preserve everything kotlin-reflect
# needs.
-keep class com.squareup.moshi.** { *; }
-keep,allowobfuscation interface com.squareup.moshi.JsonAdapter
-keep,allowobfuscation,allowshrinking interface com.squareup.moshi.JsonAdapter$Factory
-keep @com.squareup.moshi.JsonClass class *
-keep class **JsonAdapter {
    <init>(...);
    <fields>;
}
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}
# Keep EVERY property of every JsonClass-annotated DTO — including
# property names — so kotlin-reflect can match JSON keys to fields.
-keepclassmembers,allowoptimization @com.squareup.moshi.JsonClass class * { *; }

# All our data DTOs (TMDB, Xtream, Canada license, sports, requests).
-keep class com.hushtv.tv.data.**$* { *; }
-keep class com.hushtv.tv.data.** { *; }
-keepclassmembers class com.hushtv.tv.data.** {
    <init>(...);
    <fields>;
}

# kotlin-reflect runtime — moshi-kotlin uses this for its
# reflection-based adapter. Without these keeps, R8 strips builtins
# loader classes that get loaded by Class.forName().
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }
-keep class kotlin.reflect.jvm.internal.impl.builtins.BuiltInsLoaderImpl { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**
-keep class kotlin.coroutines.Continuation { *; }

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
# v1.44.98 — Conservative R8. We removed `-allowaccessmodification`
# and `-repackageclasses` after v1.44.97 lost user login state on
# update. Those two flags together let R8 move classes to a
# `h.<short>` package and change access modifiers, both of which
# break the runtime kotlin-reflect path used by KotlinJsonAdapterFactory
# for our DTOs. The remaining R8 work (dead-code removal, inlining,
# dex layout) still gives us 95% of the original perf win and the
# 13 MB APK size.

# Print which rules actually kept stuff (commented; uncomment if
# debugging an R8 crash and you need to compare).
# -printseeds  build/outputs/mapping/release/seeds.txt
# -printusage  build/outputs/mapping/release/usage.txt

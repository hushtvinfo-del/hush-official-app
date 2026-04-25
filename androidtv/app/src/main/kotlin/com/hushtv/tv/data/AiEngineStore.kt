package com.hushtv.tv.data

import android.content.Context

/**
 * Persists the user's preferred AI captions engine.
 *
 *   • [Engine.STANDARD] — free, ~1 s lag, runs on our Whisper-Base
 *     T4 server. Good default; works without burning AssemblyAI credits.
 *   • [Engine.REALTIME] — paid, ~300-500 ms lag, runs on AssemblyAI
 *     Universal Streaming Multilingual + auto-translates non-English
 *     to English via GPT-4o-mini server-side. Costs ~$0.15/hour the
 *     toggle is on.
 *
 * Toggle is read by [WhisperServerEngine] when starting a new session.
 * Changing the toggle mid-playback takes effect on the next start.
 */
object AiEngineStore {

    enum class Engine { STANDARD, REALTIME }

    private const val PREF = "hushtv_ai_engine"
    private const val KEY = "engine"

    fun get(ctx: Context): Engine {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return when (p.getString(KEY, null)) {
            "REALTIME" -> Engine.REALTIME
            else -> Engine.STANDARD
        }
    }

    fun set(ctx: Context, engine: Engine) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, engine.name)
            .apply()
    }
}

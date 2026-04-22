package com.hushtv.tv.data

import android.content.Context

/** Scheduled EPG reminders — fire a notification N min before a program starts. */
object ReminderStore {

    private const val PREFS = "hushtv_reminders"
    private const val KEY = "reminders"

    data class Reminder(
        val playlistId: String,
        val streamId: Int,
        val channelName: String,
        val programTitle: String,
        val programStartMs: Long
    )

    fun all(ctx: Context): List<Reminder> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { decode(it) }
    }

    fun add(ctx: Context, r: Reminder) {
        val list = all(ctx).filterNot { it.streamId == r.streamId && it.programStartMs == r.programStartMs } + r
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("|") { encode(it) })
            .apply()
    }

    fun remove(ctx: Context, streamId: Int, programStartMs: Long) {
        val list = all(ctx).filterNot { it.streamId == streamId && it.programStartMs == programStartMs }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("|") { encode(it) })
            .apply()
    }

    fun exists(ctx: Context, streamId: Int, programStartMs: Long): Boolean =
        all(ctx).any { it.streamId == streamId && it.programStartMs == programStartMs }

    private fun encode(r: Reminder): String =
        "${r.playlistId}\u001f${r.streamId}\u001f${r.channelName.replace("\u001f","")}\u001f" +
            "${r.programTitle.replace("\u001f","")}\u001f${r.programStartMs}"

    private fun decode(s: String): Reminder? {
        val parts = s.split('\u001f')
        if (parts.size < 5) return null
        return Reminder(
            playlistId = parts[0],
            streamId = parts[1].toIntOrNull() ?: return null,
            channelName = parts[2],
            programTitle = parts[3],
            programStartMs = parts[4].toLongOrNull() ?: return null
        )
    }
}

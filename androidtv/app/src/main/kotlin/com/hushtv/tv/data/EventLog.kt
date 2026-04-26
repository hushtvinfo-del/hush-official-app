package com.hushtv.tv.data

/**
 * Tiny in-memory ring buffer of recent app events — used to give
 * crash + freeze reports context about what the user was doing
 * leading up to the failure.
 *
 * Examples of events to log:
 *   • screen mounted ("TVMainMenu")
 *   • channel tuned ("ch=Sky Sports streamId=1234")
 *   • search query
 *   • OTA update check started/finished
 *   • crash log auto-upload started/finished
 *
 * Cheap: just an ArrayDeque guarded by `synchronized`.
 */
object EventLog {
    private const val MAX = 60
    private val ring = ArrayDeque<String>(MAX)
    private val lock = Any()

    /** Add a single line to the buffer. Includes a UTC HH:mm:ss.SSS prefix. */
    fun log(tag: String, message: String) {
        val ts = java.text.SimpleDateFormat(
            "HH:mm:ss.SSS", java.util.Locale.US
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
        synchronized(lock) {
            if (ring.size >= MAX) ring.removeFirst()
            ring.addLast("[$ts] $tag: $message")
        }
    }

    /** Snapshot the current buffer as one big newline-joined string. */
    fun snapshot(): String = synchronized(lock) { ring.joinToString("\n") }

    /** Wipe — used when shipping a report so we don't double-log the
     *  same context against subsequent failures. Optional. */
    @Suppress("unused")
    fun clear() = synchronized(lock) { ring.clear() }
}

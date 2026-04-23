package com.hushtv.tv.ui.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

/**
 * On-the-fly thumbnail extractor for VOD scrubber previews.
 *
 *  • The HTTP data-source handshake in [setDataSource] can block for
 *    5–15 seconds on slow networks, so the constructor is now a no-op
 *    and callers MUST invoke [initBlocking] on a background dispatcher
 *    before calling [extract]. Previously the constructor did the
 *    handshake, freezing the main thread at player launch and triggering
 *    ANR crashes.
 *  • Frames are rounded to the nearest 5 s bucket and cached (LRU, 30
 *    entries) so back-scrubbing across the same stretch is free.
 *  • `getFrameAtTime` is synchronous and can take 200–500 ms on Android
 *    TV hardware. Callers MUST run [extract] on `Dispatchers.IO`.
 *
 * The retriever supports HTTP(S) URLs directly for MP4/MKV containers,
 * which covers the vast majority of Xtream VOD streams. HLS/MPEG-TS live
 * streams are not supported — callers should gate on [isUrlSupported].
 */
class ThumbnailExtractor {

    private var retriever: MediaMetadataRetriever? = null
    @Volatile private var ready = false

    private val cache = object : LinkedHashMap<Long, Bitmap>(
        /* initialCapacity = */ 32,
        /* loadFactor = */ 0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<Long, Bitmap>): Boolean {
            if (size <= MAX_ENTRIES) return false
            runCatching { eldest.value.recycle() }
            return true
        }
    }

    /**
     * Open the HTTP data source. Call ONCE from a background dispatcher
     * (e.g. `Dispatchers.IO`). Safe to call multiple times — subsequent
     * calls are a no-op. Returns true on success.
     */
    fun initBlocking(url: String): Boolean {
        if (ready) return true
        val r = MediaMetadataRetriever()
        val ok = runCatching {
            r.setDataSource(url, emptyMap<String, String>())
        }.isSuccess
        if (!ok) {
            runCatching { r.release() }
            return false
        }
        retriever = r
        ready = true
        return true
    }

    /**
     * Extract a 320×180-ish frame at [positionMs]. Returns null if the
     * retriever isn't ready yet, failed to open, or the codec doesn't
     * support frame extraction.
     */
    fun extract(positionMs: Long): Bitmap? {
        if (!ready) return null
        val r = retriever ?: return null
        val key = (positionMs / BUCKET_MS) * BUCKET_MS
        cache[key]?.let { return it }
        val raw = runCatching {
            r.getFrameAtTime(key * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }.getOrNull() ?: return null
        val scaled = runCatching {
            Bitmap.createScaledBitmap(raw, THUMB_W_PX, THUMB_H_PX, true)
        }.getOrNull() ?: raw
        if (scaled !== raw) runCatching { raw.recycle() }
        cache[key] = scaled
        return scaled
    }

    fun release() {
        ready = false
        runCatching { retriever?.release() }
        retriever = null
        cache.values.forEach { runCatching { it.recycle() } }
        cache.clear()
    }

    companion object {
        private const val BUCKET_MS = 5_000L
        private const val MAX_ENTRIES = 30
        private const val THUMB_W_PX = 320
        private const val THUMB_H_PX = 180

        /**
         * MediaMetadataRetriever supports MP4 / MKV / WEBM over HTTP.
         * HLS (.m3u8), MPEG-TS (.ts), and DASH (.mpd) are NOT supported
         * — attempting to open them can freeze the thread indefinitely.
         * Skip scrubber-thumbnail extraction for those cases.
         */
        fun isUrlSupported(url: String): Boolean {
            val lower = url.lowercase().substringBefore('?')
            if (lower.endsWith(".m3u8") || lower.endsWith(".ts") ||
                lower.endsWith(".mpd") || lower.contains("/hls/")
            ) return false
            return true
        }
    }
}

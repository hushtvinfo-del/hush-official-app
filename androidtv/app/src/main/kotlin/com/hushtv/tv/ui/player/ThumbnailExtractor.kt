package com.hushtv.tv.ui.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

/**
 * On-the-fly thumbnail extractor for VOD scrubber previews.
 *
 *  • Uses a single [MediaMetadataRetriever] for the life of the player —
 *    `setDataSource` over HTTP is expensive, we only pay it once.
 *  • Frames are rounded to the nearest 5 s bucket and cached (LRU, 30 entries)
 *    so back-scrubbing across the same stretch is free.
 *  • `getFrameAtTime` is synchronous and can take 200–500 ms on Android TV
 *    hardware. Callers MUST run [extract] on `Dispatchers.IO`.
 *
 * The retriever supports HTTP(S) URLs directly for MP4/MKV containers, which
 * covers the vast majority of Xtream VOD streams. HLS/MPEG-TS live streams
 * are not supported — this helper is VOD-only.
 */
class ThumbnailExtractor(url: String) {

    private val retriever: MediaMetadataRetriever? = runCatching {
        MediaMetadataRetriever().apply { setDataSource(url, emptyMap<String, String>()) }
    }.getOrNull()

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
     * Extract a 320×180-ish frame at [positionMs]. Returns null if the
     * retriever failed (unsupported codec, network error, stream doesn't
     * support frame extraction).
     */
    fun extract(positionMs: Long): Bitmap? {
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
        runCatching { retriever?.release() }
        cache.values.forEach { runCatching { it.recycle() } }
        cache.clear()
    }

    companion object {
        private const val BUCKET_MS = 5_000L
        private const val MAX_ENTRIES = 30
        private const val THUMB_W_PX = 320
        private const val THUMB_H_PX = 180
    }
}

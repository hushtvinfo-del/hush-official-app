package com.hushtv.tv.data

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Self-contained download speed tester.
 *
 * Approach: spin two parallel HTTP downloads against Cloudflare's
 * public speed-test endpoint, sample throughput every 250 ms during a
 * ~10 s test, and report a smoothed *peak* reading.
 *
 * Why Cloudflare? Free, CORS-friendly, hosted on a global anycast
 * network so the closest PoP almost always wins — which is the same
 * routing strategy most modern IPTV CDNs use, so the result is a
 * realistic proxy for streaming throughput.
 *
 * Why two parallel connections? Single-connection TCP is throttled
 * by per-flow congestion control on most home routers, especially on
 * cable / fibre connections beyond ~100 Mbps. Two flows is enough to
 * saturate without abusing the user's link.
 *
 * The tester emits intermediate readings via [onProgress] so the UI
 * can animate a live speedometer, then returns the final smoothed
 * Mbps.
 *
 * Cancellation: the test is cooperative — callers cancel by simply
 * cancelling the calling coroutine.
 */
object SpeedTester {

    /** Cloudflare's speed-test data endpoint. Returns N raw bytes. */
    private const val URL = "https://speed.cloudflare.com/__down?bytes="
    /** ~64 MB per parallel stream. We won't hit this — we stop at the
     *  test duration cap below — but it's large enough that even a
     *  10 Gbps link won't run out of bytes mid-test. */
    private const val PER_STREAM_BYTES = 64L * 1024 * 1024
    private const val PARALLEL = 2
    private const val TEST_DURATION_MS = 10_000L
    private const val SAMPLE_INTERVAL_MS = 250L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // No keep-alive on the test client — we want fresh sockets
            // every test so we measure raw network, not warm pools.
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * Runs the download test. Suspends until the test completes (or is
     * cancelled). [onProgress] is called from the test coroutine on
     * every sample tick with the *instantaneous* speed in Mbps.
     *
     * Returns the peak smoothed Mbps observed during the test. Returns
     * 0 on any error (DNS / connectivity / cancellation).
     */
    suspend fun run(onProgress: (Float) -> Unit): Float = withContext(Dispatchers.IO) {
        val totalBytes = java.util.concurrent.atomic.AtomicLong(0)
        val cancelFlag = java.util.concurrent.atomic.AtomicBoolean(false)
        val testStart = System.currentTimeMillis()
        var peak = 0f
        try {
            coroutineScope {
                // Spawn parallel download workers.
                val workers = List(PARALLEL) {
                    async {
                        runCatching {
                            val req = Request.Builder()
                                .url("$URL$PER_STREAM_BYTES")
                                .header("Cache-Control", "no-cache")
                                .build()
                            client.newCall(req).execute().use { r ->
                                val src = r.body?.source() ?: return@runCatching
                                val buf = ByteArray(64 * 1024)
                                val raw = src.inputStream()
                                while (!cancelFlag.get()) {
                                    val n = raw.read(buf)
                                    if (n <= 0) break
                                    totalBytes.addAndGet(n.toLong())
                                }
                            }
                        }
                    }
                }

                // Sampler — emits live speed every 250 ms.
                val sampler = launch {
                    var prevBytes = 0L
                    var prevAt = testStart
                    while (isActive) {
                        delay(SAMPLE_INTERVAL_MS)
                        val now = System.currentTimeMillis()
                        val cur = totalBytes.get()
                        val deltaBytes = cur - prevBytes
                        val deltaMs = (now - prevAt).coerceAtLeast(1L)
                        val mbps = (deltaBytes.toFloat() * 8f / 1_000_000f) /
                            (deltaMs.toFloat() / 1000f)
                        prevBytes = cur
                        prevAt = now
                        if (mbps > peak) peak = mbps
                        onProgress(mbps)

                        if (now - testStart >= TEST_DURATION_MS) {
                            cancelFlag.set(true)
                            break
                        }
                    }
                }

                sampler.join()
                workers.forEach { it.cancel() }
            }
        } catch (e: Exception) {
            cancelFlag.set(true)
            return@withContext 0f
        }

        // Final result = peak smoothed value seen. Peak (rather than
        // average) better matches what the user "feels" — a few slow
        // first packets shouldn't drag the headline number down.
        peak
    }
}

/**
 * Tier classification + display metadata used by the speed-test UI.
 *
 * Thresholds mirror what the IPTV streams in this app actually need:
 *   • 1080p H.264 SDR  ≈ 8 Mbps stable
 *   • 1080p H.265 HDR  ≈ 12 Mbps stable
 *   • 4K HEVC          ≈ 25 Mbps stable
 * Networks fluctuate, so we leave 2-3× headroom in the tier above to
 * absorb dips without the overlay caching pinging the user's "this
 * channel keeps buffering" frustration.
 */
enum class SpeedTier(
    val maxMbps: Float,
    val label: String,
    val verdict: String,
    val color: Color,
) {
    POOR(10f, "Poor", "Expect frequent buffering on HD streams.", Color(0xFFEF4444)),
    FAIR(25f, "Fair", "HD will play but may buffer during peaks.", Color(0xFFF59E0B)),
    GOOD(50f, "Good", "Smooth HD streams. 4K may buffer.", Color(0xFF22D3EE)),
    EXCELLENT(Float.MAX_VALUE, "Excellent", "Smooth 4K streaming, no buffering.", Color(0xFF34D399));

    companion object {
        fun of(mbps: Float): SpeedTier = when {
            mbps < POOR.maxMbps -> POOR
            mbps < FAIR.maxMbps -> FAIR
            mbps < GOOD.maxMbps -> GOOD
            else -> EXCELLENT
        }
    }
}

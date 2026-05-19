package com.hushtv.tv.demo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.hushtv.tv.R
import com.hushtv.tv.MainActivity
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Foreground service that owns the [MediaProjection] for the duration
 * of a demo recording.
 *
 * Layout:
 *   • Video: H.264 via [MediaCodec] on a Surface fed by a VirtualDisplay
 *     (1080p @ 60 fps, 12 Mbps).
 *   • Audio: AAC encoded from [AudioPlaybackCaptureConfiguration]
 *     (Android 10+). System playback audio of THIS process is captured.
 *     We don't need other apps' audio — the demo plays our own player
 *     sounds + click chimes only.
 *   • Muxing: [MediaMuxer] writes both tracks to an MP4 in the app's
 *     external files dir (no extra storage permission needed).
 *
 * On stop the file is uploaded via [DemoUploadClient] in a background
 * thread, then we self-stop the service.
 */
class ScreenRecordingService : Service() {

    companion object {
        private const val TAG = "DemoService"
        const val ACTION_START = "com.hushtv.tv.demo.START"
        const val ACTION_STOP  = "com.hushtv.tv.demo.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CH_ID  = "hushtv_demo_recording"
        private const val NOTIF_ID = 4711

        // Recording config.
        private const val VIDEO_W = 1920
        private const val VIDEO_H = 1080
        private const val VIDEO_BITRATE = 12_000_000
        private const val VIDEO_FPS = 60
        private const val VIDEO_IFRAME_INTERVAL = 2  // seconds

        private const val AUDIO_BITRATE = 128_000
        private const val AUDIO_SAMPLE_RATE = 44_100
        private const val AUDIO_CHANNELS = 2

        @Volatile
        private var appCtx: Context? = null
        fun appContextOrNull(): Context? = appCtx
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var muxer: MediaMuxer? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    @Volatile private var muxerStarted = false
    private val running = AtomicBoolean(false)

    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    private var outputFile: File? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        appCtx = applicationContext
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (running.get()) return START_NOT_STICKY
                startForegroundCompat()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    fail("Demo recorder needs Android 10+")
                    return START_NOT_STICKY
                }
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (code == Int.MIN_VALUE || data == null) {
                    fail("Missing projection token")
                    return START_NOT_STICKY
                }
                try {
                    startRecording(code, data)
                } catch (t: Throwable) {
                    Log.e(TAG, "startRecording failed", t)
                    fail("Recorder failed: ${t.message}")
                }
            }
            ACTION_STOP -> {
                stopRecordingAndUpload()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            val existing = nm.getNotificationChannel(CH_ID)
            if (existing == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CH_ID, "Demo recording",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "Active while a marketing demo is being recorded" }
                )
            }
        }
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // "Stop" action that fires ACTION_STOP back at this service.
        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CH_ID)
                .setContentTitle("HushTV screen recorder")
                .setContentText("Recording — tap Stop to save")
                .setSmallIcon(R.drawable.tv_banner)
                .setContentIntent(tap)
                .setOngoing(true)
                .addAction(
                    Notification.Action.Builder(
                        null as android.graphics.drawable.Icon?,
                        "Stop", stopPending,
                    ).build()
                )
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("HushTV screen recorder")
                .setContentText("Recording — tap Stop to save")
                .setSmallIcon(R.drawable.tv_banner)
                .setContentIntent(tap)
                .setOngoing(true)
                .addAction(0, "Stop", stopPending)
                .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startRecording(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(resultCode, data) ?: run {
            fail("Could not obtain MediaProjection"); return
        }
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection.onStop — stopping recording")
                main.post { stopRecordingAndUpload() }
            }
        }, main)
        projection = proj

        // Output file.
        val dir = File(getExternalFilesDir(null), "HushTV-Demo").apply { mkdirs() }
        val fname = "demo-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) +
            ".mp4"
        val file = File(dir, fname)
        outputFile = file

        // Video encoder.
        val vFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_W, VIDEO_H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_IFRAME_INTERVAL)
        }
        val vEnc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        vEnc.configure(vFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val vSurface = vEnc.createInputSurface()
        vEnc.start()
        videoEncoder = vEnc

        // VirtualDisplay sized to device DPI (recordings are 1080p in pixels).
        val dm = resources.displayMetrics
        virtualDisplay = proj.createVirtualDisplay(
            "HushTVDemoVD",
            VIDEO_W, VIDEO_H, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            vSurface, null, null,
        )

        // Audio encoder + capture.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val aFmt = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                AUDIO_SAMPLE_RATE, AUDIO_CHANNELS,
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO)
            }
            val aEnc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            aEnc.configure(aFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aEnc.start()
            audioEncoder = aEnc

            val cfg = AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            audioRecord = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(AUDIO_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .setAudioPlaybackCaptureConfig(cfg)
                .build()
                .also { it.startRecording() }
        }

        // Muxer.
        muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        running.set(true)
        DemoController.onServiceRecordingStarted()

        videoThread = thread(name = "demo-video", isDaemon = true) { drainVideo() }
        if (audioEncoder != null) {
            audioThread = thread(name = "demo-audio", isDaemon = true) { drainAudio() }
        }
    }

    private fun drainVideo() {
        val enc = videoEncoder ?: return
        val info = MediaCodec.BufferInfo()
        try {
            while (running.get()) {
                val idx = enc.dequeueOutputBuffer(info, 10_000)
                when {
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(this) {
                            if (videoTrackIndex < 0) {
                                videoTrackIndex = muxer!!.addTrack(enc.outputFormat)
                                maybeStartMuxer()
                            }
                        }
                    }
                    idx >= 0 -> {
                        val buf = enc.getOutputBuffer(idx) ?: continue
                        writeSample(buf, info, videoTrackIndex)
                        enc.releaseOutputBuffer(idx, false)
                    }
                    else -> {} // try again later
                }
            }
            // Flush remaining.
            try { enc.signalEndOfInputStream() } catch (_: Throwable) {}
            while (true) {
                val idx = enc.dequeueOutputBuffer(info, 5_000)
                if (idx < 0) break
                val buf = enc.getOutputBuffer(idx) ?: break
                writeSample(buf, info, videoTrackIndex)
                enc.releaseOutputBuffer(idx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } catch (t: Throwable) {
            Log.w(TAG, "video drain ended: ${t.message}")
        }
    }

    private fun drainAudio() {
        val enc = audioEncoder ?: return
        val rec = audioRecord ?: return
        val info = MediaCodec.BufferInfo()
        val pcmBuf = ByteArray(2048)
        try {
            while (running.get()) {
                // Push PCM into the encoder.
                val inIdx = enc.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val ib: ByteBuffer? = enc.getInputBuffer(inIdx)
                    ib?.clear()
                    val n = rec.read(pcmBuf, 0, pcmBuf.size)
                    if (n > 0) {
                        ib?.put(pcmBuf, 0, n)
                        enc.queueInputBuffer(inIdx, 0, n, System.nanoTime() / 1000, 0)
                    } else {
                        enc.queueInputBuffer(inIdx, 0, 0, System.nanoTime() / 1000, 0)
                    }
                }
                // Drain output.
                while (true) {
                    val outIdx = enc.dequeueOutputBuffer(info, 0)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            synchronized(this) {
                                if (audioTrackIndex < 0) {
                                    audioTrackIndex = muxer!!.addTrack(enc.outputFormat)
                                    maybeStartMuxer()
                                }
                            }
                        }
                        outIdx >= 0 -> {
                            val buf = enc.getOutputBuffer(outIdx) ?: break
                            writeSample(buf, info, audioTrackIndex)
                            enc.releaseOutputBuffer(outIdx, false)
                        }
                        else -> break
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "audio drain ended: ${t.message}")
        }
    }

    private fun maybeStartMuxer() {
        // If there's an audio encoder we wait for both tracks. Otherwise
        // start as soon as the video track is ready.
        val ready = if (audioEncoder != null) {
            videoTrackIndex >= 0 && audioTrackIndex >= 0
        } else videoTrackIndex >= 0
        if (ready && !muxerStarted) {
            muxer!!.start()
            muxerStarted = true
            Log.i(TAG, "muxer started (video=$videoTrackIndex audio=$audioTrackIndex)")
        }
    }

    private fun writeSample(buf: ByteBuffer, info: MediaCodec.BufferInfo, trackIndex: Int) {
        if (!muxerStarted || trackIndex < 0) return
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return
        if (info.size <= 0) return
        buf.position(info.offset)
        buf.limit(info.offset + info.size)
        try {
            synchronized(this) { muxer?.writeSampleData(trackIndex, buf, info) }
        } catch (t: Throwable) {
            Log.w(TAG, "writeSample dropped: ${t.message}")
        }
    }

    private fun stopRecordingAndUpload() {
        if (!running.compareAndSet(true, false)) {
            // Could be the "stopped before started" path — clean up and bail.
            cleanup()
            DemoController.onServiceStopped(null, null)
            stopForegroundCompat()
            stopSelf()
            return
        }
        // Let the drain threads notice running=false and flush.
        videoThread?.join(2_500)
        audioThread?.join(2_500)
        // Tear everything down on a background thread (muxer.stop may block).
        Thread({
            cleanup()
            val file = outputFile
            val ok = file?.exists() == true && (file.length() > 0L)
            val absPath = if (ok) file!!.absolutePath else null
            if (ok) {
                DemoController.setUploadStatus("Saved ${humanSize(file!!.length())}, uploading…")
                val res = DemoUploadClient.upload(applicationContext, file)
                if (res.success) {
                    DemoController.setUploadStatus("Uploaded id=${res.recordId ?: "?"} (${humanSize(file.length())})")
                } else if (res.skipped) {
                    DemoController.setUploadStatus("Upload skipped (no URL configured)")
                } else {
                    DemoController.setUploadStatus("Upload failed: ${res.error ?: "?"}")
                }
            }
            main.post {
                DemoController.onServiceStopped(absPath, if (ok) null else "Empty MP4 — recorder didn't start")
                stopForegroundCompat()
                stopSelf()
            }
        }, "demo-finalise").start()
    }

    private fun cleanup() {
        try { videoEncoder?.stop() } catch (_: Throwable) {}
        try { videoEncoder?.release() } catch (_: Throwable) {}
        videoEncoder = null

        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null

        try { audioEncoder?.stop() } catch (_: Throwable) {}
        try { audioEncoder?.release() } catch (_: Throwable) {}
        audioEncoder = null

        try { virtualDisplay?.release() } catch (_: Throwable) {}
        virtualDisplay = null

        try {
            if (muxerStarted) muxer?.stop()
        } catch (_: Throwable) {}
        try { muxer?.release() } catch (_: Throwable) {}
        muxer = null
        muxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1

        try { projection?.stop() } catch (_: Throwable) {}
        projection = null
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    private fun fail(reason: String) {
        running.set(false)
        cleanup()
        Log.e(TAG, reason)
        main.post {
            DemoController.onServiceStopped(null, reason)
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun humanSize(b: Long): String {
        val u = arrayOf("B", "KB", "MB", "GB")
        var x = b.toDouble(); var i = 0
        while (x >= 1024 && i < u.lastIndex) { x /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", x, u[i])
    }

    override fun onDestroy() {
        super.onDestroy()
        running.set(false)
        cleanup()
    }
}

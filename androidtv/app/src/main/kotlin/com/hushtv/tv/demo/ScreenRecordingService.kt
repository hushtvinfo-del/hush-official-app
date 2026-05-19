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
import android.media.MediaRecorder
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that performs a manual screen recording using
 * Android's high-level [MediaRecorder] API.
 *
 * Why MediaRecorder and not MediaCodec+MediaMuxer:
 *
 * Android 11 (NVIDIA SHIELD's SDK level) has a known NPE inside
 * MediaCodec's internal DisplayListener that fires whenever a fresh
 * VirtualDisplay is created with a MediaCodec input surface — the
 * Display$Mode is null for VirtualDisplays before they're fully
 * initialized and the listener calls .getMode() unconditionally.
 * The crash:
 *
 *   NullPointerException: ...DisplayInfo.getMode() on null
 *     at android.media.MediaCodec$1.onDisplayChanged(MediaCodec.java:2143)
 *
 * MediaRecorder handles everything internally (encoder, muxer,
 * surface plumbing) without triggering this listener path. It's
 * the recipe used in every well-tested screen-recorder app and
 * Just Works on Android 10+.
 *
 * Trade-off: MediaRecorder doesn't expose
 * AudioPlaybackCaptureConfiguration. Audio capture is dropped for
 * v1.44.93. If the user wants system audio later we can add it as
 * a second AudioRecord encoded into a separate AAC file and muxed
 * server-side, which avoids the MediaCodec NPE entirely.
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

        // Recording config. 1080p / 30 fps / 8 Mbps gives a ~60 MB file
        // for a 60-second clip and works on virtually every Android TV.
        // Was 60 fps / 12 Mbps but we dial back so older Shields don't
        // run out of encoder bandwidth halfway through.
        private const val VIDEO_W = 1920
        private const val VIDEO_H = 1080
        private const val VIDEO_BITRATE = 8_000_000
        private const val VIDEO_FPS = 30
        private const val VIDEO_IFRAME_INTERVAL = 2  // seconds

        @Volatile
        private var appCtx: Context? = null
        fun appContextOrNull(): Context? = appCtx
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private val running = AtomicBoolean(false)
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
                    fail("Recorder failed to start: ${t.message ?: t.javaClass.simpleName}")
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
            if (nm.getNotificationChannel(CH_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CH_ID, "Screen recording",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "Active while a screen recording is in progress" }
                )
            }
        }
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
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

    private fun startRecording(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java) ?: run {
            fail("MediaProjectionManager unavailable"); return
        }
        val proj = mpm.getMediaProjection(resultCode, data) ?: run {
            fail("Could not obtain MediaProjection"); return
        }
        // Required on Android 14+: must register a callback before
        // touching createVirtualDisplay or the OS revokes the token.
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection.onStop — finishing")
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

        // Configure MediaRecorder (video only — no audio in v1.44.93).
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        rec.setVideoSize(VIDEO_W, VIDEO_H)
        rec.setVideoFrameRate(VIDEO_FPS)
        rec.setVideoEncodingBitRate(VIDEO_BITRATE)
        // The H.264 keyframe interval default is ~2s — explicit if available.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            rec.setVideoEncodingProfileLevel(
                android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel4,
            )
        }
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        recorder = rec

        // Build the VirtualDisplay AFTER prepare() so the encoder's
        // input surface exists. VirtualDisplay is destroyed in cleanup().
        val dpi = resources.displayMetrics.densityDpi
        virtualDisplay = proj.createVirtualDisplay(
            "HushTVRecorderVD",
            VIDEO_W, VIDEO_H, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            rec.surface, null, null,
        )
        rec.start()

        running.set(true)
        DemoController.onServiceRecordingStarted()
    }

    private fun stopRecordingAndUpload() {
        if (!running.compareAndSet(true, false)) {
            // "Stop before start" — clean up and bail.
            cleanup()
            DemoController.onServiceStopped(null, null)
            stopForegroundCompat()
            stopSelf()
            return
        }
        Thread({
            // Stop the recorder cleanly. MediaRecorder.stop() can throw
            // IllegalStateException if it never received a frame (e.g.
            // the user stopped within 200 ms of start). Guard for it.
            val rec = recorder
            var stopOk = true
            try { rec?.stop() } catch (t: Throwable) {
                Log.w(TAG, "recorder.stop() threw: ${t.message}")
                stopOk = false
            }
            cleanup()

            val file = outputFile
            val ok = stopOk && file?.exists() == true && (file.length() > 0L)
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
                DemoController.onServiceStopped(
                    absPath,
                    if (ok) null else "Empty MP4 — recording ended too early",
                )
                stopForegroundCompat()
                stopSelf()
            }
        }, "demo-finalise").start()
    }

    private fun cleanup() {
        try { recorder?.release() } catch (_: Throwable) {}
        recorder = null
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        virtualDisplay = null
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

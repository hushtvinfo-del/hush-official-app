package com.hushtv.tv.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.Inter

/**
 * In-app YouTube trailer player.
 *
 * Why a WebView + IFrame and not direct ExoPlayer playback?
 *   • YouTube ToS / API terms forbid scraping a direct video URL
 *     and feeding it into ExoPlayer. Any extractor library
 *     (NewPipe, etc.) is in violation and breaks every few weeks
 *     when YouTube rotates its signature deciphering.
 *   • The official, Google-blessed embed path on Android is
 *     either the deprecated YouTubeAndroidPlayerAPI (defunct on
 *     most Fire TVs) or the IFrame Player API loaded inside a
 *     WebView. The IFrame path is the only one that survives.
 *
 * The page below is a tiny self-contained HTML payload that
 * boots the IFrame player full-bleed and auto-plays. We pass the
 * YouTube `videoId` via a fixed [URL] parameter inline rather
 * than via JS post-message so the page works offline-cached.
 *
 * D-pad behavior:
 *   • BACK or D-pad-LEFT exits the player and returns to detail.
 *   • Any other key falls through to the WebView so YouTube's
 *     own embedded controls (play/pause, volume, etc.) get the
 *     event. This means user can pause via the OK button.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TVTrailerPlayerScreen(
    nav: NavController,
    videoId: String,
    title: String? = null,
) {
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { nav.popBackStack() }

    // Keep the screen awake while the trailer plays — same Fire Stick
    // screensaver mitigation as the main player.
    val trailerCtx = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val activity = trailerCtx as? android.app.Activity
        activity?.window?.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        onDispose {
            activity?.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    Box(Modifier.fillMaxSize().background(BgBlack)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        // Modern UA helps YouTube serve TV-friendly chrome.
                        userAgentString = userAgentString
                            .replace("; wv", "") // hide WebView marker
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?,
                        ) {
                            failed = true
                            loading = false
                        }
                    }
                    val html = buildIframeHtml(videoId)
                    loadDataWithBaseURL(
                        "https://www.youtube.com",
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
        )

        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Cyan, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    "Loading trailer…",
                    color = Color(0xFFCBD5E1),
                    fontFamily = Inter,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (title != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        title,
                        color = Color(0xFF94A3B8),
                        fontFamily = Inter,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        if (failed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Couldn't load this trailer",
                        color = Color.White,
                        fontFamily = Inter,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "YouTube may be blocking embedded playback.",
                        color = Color(0xFF94A3B8),
                        fontFamily = Inter,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }

    // Defensive: if the player never reports onPageFinished within
    // 10 s (network stall, JS blocked), flip to error so user has a
    // way out via BACK.
    LaunchedEffect(videoId) {
        kotlinx.coroutines.delay(10_000)
        if (loading) failed = true
    }
}

/**
 * Tiny bootstrap HTML that mounts the YouTube IFrame Player at
 * 100% × 100% and autoplays. Uses the official `iframe_api` JS so
 * Google sees this as a sanctioned embed, not scraping.
 */
private fun buildIframeHtml(videoId: String): String = """
    <!doctype html>
    <html>
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
      <style>
        html, body { margin:0; padding:0; height:100%; width:100%; background:#000; overflow:hidden; }
        #player { position:absolute; top:0; left:0; width:100%; height:100%; }
      </style>
    </head>
    <body>
      <div id="player"></div>
      <script src="https://www.youtube.com/iframe_api"></script>
      <script>
        var player;
        function onYouTubeIframeAPIReady() {
          player = new YT.Player('player', {
            videoId: '$videoId',
            playerVars: {
              autoplay: 1,
              controls: 1,
              fs: 0,
              modestbranding: 1,
              rel: 0,
              playsinline: 1,
              iv_load_policy: 3
            },
            events: {
              onReady: function(e) { e.target.playVideo(); }
            }
          });
        }
      </script>
    </body>
    </html>
""".trimIndent()

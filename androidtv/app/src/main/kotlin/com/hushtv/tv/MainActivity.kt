package com.hushtv.tv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hushtv.tv.data.LastChannelStore
import com.hushtv.tv.ui.HushSplashScreen
import com.hushtv.tv.ui.screens.TVAddAccountScreen
import com.hushtv.tv.ui.screens.TVBrowseScreen
import com.hushtv.tv.ui.screens.TVEpgGridScreen
import com.hushtv.tv.ui.screens.TVHomeScreen
import com.hushtv.tv.ui.screens.TVLiveBrowseScreen
import com.hushtv.tv.ui.screens.TVMainMenuScreen
import com.hushtv.tv.ui.screens.TVMovieDetailScreen
import com.hushtv.tv.ui.screens.TVPlayerScreen
import com.hushtv.tv.ui.screens.TVSeriesDetailScreen
import com.hushtv.tv.ui.screens.TVSettingsScreen
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.ui.theme.HushTVTheme
import com.hushtv.tv.update.UpdateDialog
import com.hushtv.tv.update.UpdateManager
import com.hushtv.tv.update.VersionInfo
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate(). Instantly shows the pure-black
        // splash background — the animated wordmark is rendered by Compose.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            HushTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(BgBlack),
                    color = BgBlack,
                ) {
                    var splashDone by remember { mutableStateOf(false) }

                    if (!splashDone) {
                        HushSplashScreen(onDone = { splashDone = true })
                    } else {
                        AppContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppContent() {
    val ctx = LocalContext.current
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") { TVHomeScreen(nav) }
        composable("add") { TVAddAccountScreen(nav) }
        composable("menu/{playlistId}") { bs ->
            TVMainMenuScreen(nav, bs.arguments?.getString("playlistId") ?: "")
        }
        composable("browse/{playlistId}/{type}") { bs ->
            val type = bs.arguments?.getString("type") ?: "live"
            val playlistId = bs.arguments?.getString("playlistId") ?: ""
            if (type == "live") {
                TVLiveBrowseScreen(nav, playlistId)
            } else {
                TVBrowseScreen(nav, playlistId, type)
            }
        }
        composable("series/{playlistId}/{seriesId}/{seriesName}") { bs ->
            TVSeriesDetailScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
                bs.arguments?.getString("seriesId") ?: "",
                bs.arguments?.getString("seriesName") ?: "",
            )
        }
        composable("moviedetail/{playlistId}/{streamId}/{title}") { bs ->
            TVMovieDetailScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
                bs.arguments?.getString("streamId")?.toIntOrNull() ?: 0,
                bs.arguments?.getString("title") ?: "",
            )
        }
        composable("epg/{playlistId}") { bs ->
            TVEpgGridScreen(nav, bs.arguments?.getString("playlistId") ?: "")
        }
        composable("settings/{playlistId}") { bs ->
            TVSettingsScreen(nav, bs.arguments?.getString("playlistId") ?: "")
        }
        composable("player/{playlistId}/{streamUrl}/{channelName}/{isLive}") { bs ->
            TVPlayerScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
                bs.arguments?.getString("streamUrl") ?: "",
                bs.arguments?.getString("channelName") ?: "",
                bs.arguments?.getString("isLive") == "true",
            )
        }
    }

    // Auto-resume: if the user was watching a live channel last time,
    // skip the Home → Menu → Browse → Player chain and boot straight
    // into that channel fullscreen. Just like turning on a real TV.
    var resumeAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (resumeAttempted) return@LaunchedEffect
        resumeAttempted = true
        val last = LastChannelStore.load(ctx) ?: return@LaunchedEffect
        nav.navigate(
            "player/${last.playlistId}" +
                "/${Uri.encode(last.streamUrl)}" +
                "/${Uri.encode(last.channelName)}" +
                "/true"
        )
    }

    UpdateCheckHost()
}

/** Fetches /version.json after a small delay (so UI is already rendered) and
 *  shows the update dialog if a newer version is available. */
@Composable
private fun UpdateCheckHost() {
    var info by remember { mutableStateOf<VersionInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3000)
        val latest = UpdateManager.fetchLatest() ?: return@LaunchedEffect
        if (UpdateManager.isUpdateAvailable(latest)) info = latest
    }

    val currentInfo = info
    if (currentInfo != null && !dismissed) {
        UpdateDialog(
            info = currentInfo,
            onDismiss = { dismissed = true },
        )
    }
}

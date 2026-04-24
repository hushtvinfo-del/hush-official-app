package com.hushtv.tv

import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.hushtv.tv.data.LastChannelStore
import com.hushtv.tv.data.LastProfileStore
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.mobile.MobileApp
import com.hushtv.tv.ui.HushSplashScreen
import com.hushtv.tv.ui.screens.TVAddAccountScreen
import com.hushtv.tv.ui.screens.TVBrowseScreen
import com.hushtv.tv.ui.screens.TVCollectionDetailScreen
import com.hushtv.tv.ui.screens.TVCollectionsBrowseScreen
import com.hushtv.tv.ui.screens.TVEpgGridScreen
import com.hushtv.tv.ui.screens.TVUnifiedSearchScreen
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
                    val ctx = LocalContext.current
                    val config = LocalConfiguration.current

                    // Form factor detection. We route TV boxes (Leanback +
                    // UI_MODE_TYPE_TELEVISION) to the original AppContent
                    // and phones/tablets to MobileApp. The shortest-width
                    // check catches folding-phone landscape as mobile too,
                    // since <600dp in ANY orientation means we don't have
                    // room for the TV layouts.
                    val isTv = remember {
                        val pm = ctx.packageManager
                        val leanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                            || pm.hasSystemFeature("android.software.leanback_only")
                        val uiModeTv = (ctx.resources.configuration.uiMode and
                            Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
                        val wideEnough = config.smallestScreenWidthDp >= 600
                        // TV if either Leanback is present, OR UI mode is TV,
                        // OR the shortest screen dimension is large enough
                        // that the TV layout is the right call (tablets ≥ 600dp).
                        leanback || uiModeTv || wideEnough
                    }

                    if (!splashDone) {
                        HushSplashScreen(onDone = { splashDone = true })
                    } else {
                        if (isTv) AppContent() else MobileApp()
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

    // Determine the start destination BEFORE composing the NavHost so the
    // profile picker never flashes on screen for returning users.
    //
    //   • Valid saved profile → boot straight into that profile's menu.
    //     The picker is NOT in the back stack → BACK exits the app.
    //   • No saved profile (first run or after a wipe) → show the picker.
    val startDestination = remember {
        val id = LastProfileStore.load(ctx)
        if (id != null && PlaylistStore.find(ctx, id) != null) "menu/$id" else "home"
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable("home") { TVHomeScreen(nav) }
        composable("add") { TVAddAccountScreen(nav) }
        composable("menu/{playlistId}") { bs ->
            TVMainMenuScreen(nav, bs.arguments?.getString("playlistId") ?: "")
        }
        composable(
            route = "browse/{playlistId}/{type}?category={category}&catId={catId}",
            arguments = listOf(
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("catId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { bs ->
            val type = bs.arguments?.getString("type") ?: "live"
            val playlistId = bs.arguments?.getString("playlistId") ?: ""
            val category = bs.arguments?.getString("category")
            val catId = bs.arguments?.getString("catId")
            if (type == "live") {
                TVLiveBrowseScreen(nav, playlistId)
            } else {
                TVBrowseScreen(
                    nav,
                    playlistId,
                    type,
                    initialCategoryName = category,
                    initialCategoryId = catId,
                )
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
        composable("collection/{playlistId}/{collectionId}/{name}") { bs ->
            TVCollectionDetailScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
                bs.arguments?.getString("collectionId")?.toIntOrNull() ?: 0,
                bs.arguments?.getString("name") ?: "",
            )
        }
        composable("collections/{playlistId}") { bs ->
            TVCollectionsBrowseScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
            )
        }
        composable("search/{playlistId}") { bs ->
            TVUnifiedSearchScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
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

    // Optional: auto-resume the last-watched live channel on top of the menu
    // for the active profile. BACK from the player lands on the menu.
    var resumeAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (resumeAttempted) return@LaunchedEffect
        resumeAttempted = true
        val last = LastChannelStore.load(ctx) ?: return@LaunchedEffect
        val activeProfileId = LastProfileStore.load(ctx)
        // Only auto-resume if the channel belongs to the profile we just
        // auto-logged into (or if no profile is saved yet — preserves the
        // original "turn-on-TV" behaviour for legacy users).
        if (activeProfileId != null && last.playlistId != activeProfileId) return@LaunchedEffect
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

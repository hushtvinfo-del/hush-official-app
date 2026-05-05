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
import androidx.lifecycle.lifecycleScope
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
import com.hushtv.tv.ui.requests.RequestNotificationHost
import com.hushtv.tv.ui.requests.WatchTarget
import com.hushtv.tv.ui.screens.TVAddAccountScreen
import com.hushtv.tv.ui.screens.TVBrowseScreen
import com.hushtv.tv.ui.screens.TVCollectionDetailScreen
import com.hushtv.tv.ui.screens.TVCollectionsBrowseScreen
import com.hushtv.tv.ui.screens.TVDiagnosticsScreen
import com.hushtv.tv.ui.screens.TVEpgGridScreen
import com.hushtv.tv.ui.screens.TVUnifiedSearchScreen
import com.hushtv.tv.ui.screens.TVHomeScreen
import com.hushtv.tv.ui.screens.TVLiveBrowseScreen
import com.hushtv.tv.ui.screens.TVMainMenuScreen
import com.hushtv.tv.ui.screens.TVMovieDetailScreen
import com.hushtv.tv.ui.screens.TVPlayerScreen
import com.hushtv.tv.ui.screens.TVSeriesDetailScreen
import com.hushtv.tv.ui.screens.TVSettingsScreen
import com.hushtv.tv.ui.screens.TVSpeedTestScreen
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
        // Phase 2 / Phase 3 — start the DVR event poller so scheduled
        // recordings, completions, and skipped-due-to-conflict events
        // surface as Android system notifications.
        com.hushtv.tv.data.DvrEventPoller.start(this)
        // v1.43.82 — start cross-device sync engine.
        com.hushtv.tv.data.SyncEngine.start(this, lifecycleScope)
        // v1.43.86 — refresh server-side bundle overrides every 6 h.
        // **DISABLED in v1.43.87** — see HushTVApp.newImageLoader().
        // com.hushtv.tv.data.BundleOverrides.startRefresh(this, lifecycleScope)
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

                    // Distinguish "routed to the TV layout but actually a
                    // touch-first device" — i.e. tablets. Signal to TV
                    // composables so they can enable swipe + on-screen
                    // buttons without affecting Leanback TVs.
                    val isTouchDevice = remember {
                        val pm = ctx.packageManager
                        val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                            pm.hasSystemFeature("android.software.leanback_only")
                        val isUiTv = (ctx.resources.configuration.uiMode and
                            Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
                        val hasFingerTouch = ctx.resources.configuration.touchscreen ==
                            Configuration.TOUCHSCREEN_FINGER
                        // A real TV is either Leanback OR uiMode=TV; anything
                        // else with a finger touchscreen is a touch device
                        // (tablet, Chromebook, foldable in landscape).
                        !hasLeanback && !isUiTv && hasFingerTouch
                    }

                    if (!splashDone) {
                        HushSplashScreen(onDone = { splashDone = true })
                    } else {
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.hushtv.tv.ui.LocalIsTouchDevice provides isTouchDevice,
                        ) {
                            if (isTv) AppContent() else MobileApp()
                        }
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
    // ── Boot refresh sequence ─────────────────────────────────────
    // On every COLD start (process death → relaunch), we route the
    // user through a brief "Refreshing your library" splash that
    // wipes stale HTTP caches, re-pulls categories / catalogues,
    // re-primes the library index, etc. This is the cheapest UX
    // win we can make: every screen after boot feels instant and
    // shows real-time data, instead of whatever was in cache from
    // 3 days ago.
    //
    // The flag is process-wide (not navigation-wide) so an Activity
    // recreation (config change, deep-link relaunch) does NOT force
    // a refresh. A genuine cold start = process restart = flag
    // resets to false → boot screen shown.
    val needsBoot = remember { !BootGate.didBootRefresh }
    val startDestination = remember(needsBoot) {
        if (needsBoot) {
            "boot"
        } else {
            val id = LastProfileStore.load(ctx)
            if (id != null && PlaylistStore.find(ctx, id) != null) "menu/$id" else "home"
        }
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable("boot") {
            com.hushtv.tv.ui.boot.BootRefreshScreen(onDone = {
                BootGate.didBootRefresh = true
                val id = LastProfileStore.load(ctx)
                val target = if (id != null && PlaylistStore.find(ctx, id) != null)
                    "menu/$id" else "home"
                nav.navigate(target) {
                    popUpTo("boot") { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
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
        composable("diag") { TVDiagnosticsScreen(nav) }
        composable("speedtest") { TVSpeedTestScreen(nav) }
        composable("myrequests/{playlistId}") { bs ->
            com.hushtv.tv.ui.requests.TVMyRequestsScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
            )
        }
        composable("recordings/{playlistId}") { bs ->
            com.hushtv.tv.ui.screens.TVMyRecordingsScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
            )
        }
        composable("requests/{playlistId}") { bs ->
            com.hushtv.tv.ui.requests.TVRequestsScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
            )
        }
        composable("hushplus/{playlistId}") { bs ->
            com.hushtv.tv.ui.hushplus.TVHushPlusScreen(
                nav,
                bs.arguments?.getString("playlistId") ?: "",
            )
        }
        // HushXXX runs full-screen, outside the Hush+ sidebar layout —
        // it's effectively its own mini-app. The Hush+ addon card
        // navigates to this route instead of rendering the screen
        // inside the side-panel content area.
        composable("hushxxx/{playlistId}") { bs ->
            val playlistId = bs.arguments?.getString("playlistId") ?: ""
            var showDmca by remember { mutableStateOf(false) }
            com.hushtv.tv.ui.hushxxx.HushXxxScreen(
                onPlayScene = { url, title ->
                    val encUrl = android.net.Uri.encode(url)
                    val encTitle = android.net.Uri.encode(title)
                    nav.navigate("player/$playlistId/$encUrl/$encTitle/false")
                },
                onDmcaOpen = { showDmca = true },
                onDismiss = { nav.popBackStack() },
            )
            if (showDmca) {
                com.hushtv.tv.ui.hushxxx.HushXxxDmcaDialog(
                    onDismiss = { showDmca = false },
                )
            }
        }
        composable("themes/{playlistId}") { bs ->
            com.hushtv.tv.ui.screens.TVThemedCatalogScreen(
                nav = nav,
                playlistId = bs.arguments?.getString("playlistId") ?: "",
            )
        }
        composable("themedetail/{playlistId}/{themeId}") { bs ->
            com.hushtv.tv.ui.screens.TVThemedDetailScreen(
                nav = nav,
                playlistId = bs.arguments?.getString("playlistId") ?: "",
                themeId = bs.arguments?.getString("themeId") ?: "",
            )
        }
        composable("decadeyears/{playlistId}/{startYear}") { bs ->
            com.hushtv.tv.ui.screens.TVDecadeYearsScreen(
                nav = nav,
                playlistId = bs.arguments?.getString("playlistId") ?: "",
                decadeStartYear = bs.arguments?.getString("startYear")?.toIntOrNull() ?: 0,
            )
        }
        composable("yearmovies/{playlistId}/{year}") { bs ->
            com.hushtv.tv.ui.screens.TVYearMoviesScreen(
                nav = nav,
                playlistId = bs.arguments?.getString("playlistId") ?: "",
                year = bs.arguments?.getString("year")?.toIntOrNull() ?: 0,
            )
        }
        composable("trailer/{videoId}?title={title}") { bs ->
            com.hushtv.tv.ui.screens.TVTrailerPlayerScreen(
                nav = nav,
                videoId = bs.arguments?.getString("videoId") ?: "",
                title = bs.arguments?.getString("title")?.takeIf { it.isNotBlank() },
            )
        }
        composable("person/{playlistId}/{personId}/{name}") { bs ->
            com.hushtv.tv.ui.screens.TVPersonFilmographyScreen(
                nav = nav,
                playlistId = bs.arguments?.getString("playlistId") ?: "",
                personId = bs.arguments?.getString("personId")?.toIntOrNull() ?: 0,
                personName = Uri.decode(bs.arguments?.getString("name") ?: ""),
            )
        }
        composable("requestdetail/{playlistId}/{requestId}") { bs ->
            com.hushtv.tv.ui.requests.TVRequestDetailScreen(
                nav = nav,
                requestId = bs.arguments?.getString("requestId") ?: "",
                playlistId = bs.arguments?.getString("playlistId") ?: "",
            )
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
    // Gated behind AutoResumeStore — disabled by default since v1.42.16
    // (per user request: cold-launching straight into a stream made
    // diagnostic + UX work harder).
    var resumeAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (resumeAttempted) return@LaunchedEffect
        resumeAttempted = true
        if (!com.hushtv.tv.data.AutoResumeStore.isEnabled(ctx)) return@LaunchedEffect
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

    // In-app banner that polls the request gateway and lets the user
    // one-tap into a movie/series that just got fulfilled.
    val requestPlaylistId = remember {
        LastProfileStore.load(ctx)?.takeIf { PlaylistStore.find(ctx, it) != null }
    }
    RequestNotificationHost(
        playlistId = requestPlaylistId,
        onWatchNow = { target ->
            val pid = requestPlaylistId ?: return@RequestNotificationHost
            when (target) {
                is WatchTarget.Movie -> nav.navigate(
                    "moviedetail/$pid/${target.streamId}/${Uri.encode(target.title)}"
                )
                is WatchTarget.Series -> nav.navigate(
                    "series/$pid/${target.seriesId}/${Uri.encode(target.title)}"
                )
                WatchTarget.NotFound -> nav.navigate("myrequests/$pid")
            }
        },
    )
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

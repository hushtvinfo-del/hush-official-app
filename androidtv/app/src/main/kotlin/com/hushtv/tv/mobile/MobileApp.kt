package com.hushtv.tv.mobile

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hushtv.tv.data.LastProfileStore
import com.hushtv.tv.data.PlaylistStore
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.update.UpdateDialog
import com.hushtv.tv.update.UpdateManager
import com.hushtv.tv.update.VersionInfo
import com.hushtv.tv.ui.screens.TVAddAccountScreen
import com.hushtv.tv.ui.screens.TVHomeScreen
import kotlinx.coroutines.delay

/**
 * Entry point for mobile-phone form factor. Builds a Scaffold with a
 * bottom nav bar, touch-first screens, and a dedicated mobile player.
 * Shares the entire data layer + settings + add-account flows with the
 * TV app — only the UI trees diverge.
 *
 * The profile-picker ("home") and add-account screens are REUSED from
 * the TV package because they're simple list screens that work fine
 * on small displays. Everything under the profile shell is bespoke.
 */
@Composable
fun MobileApp() {
    val ctx = LocalContext.current
    val nav = rememberNavController()

    val startDestination = remember {
        val id = LastProfileStore.load(ctx)
        if (id != null && PlaylistStore.find(ctx, id) != null) "shell/$id" else "home"
    }

    Surface(
        modifier = Modifier.fillMaxSize().background(BgBlack),
        color = BgBlack,
    ) {
        NavHost(navController = nav, startDestination = startDestination) {
            composable("home") { TVHomeScreen(nav) }
            composable("add") { TVAddAccountScreen(nav) }
            composable("shell/{playlistId}") { bs ->
                MobileShell(nav, bs.arguments?.getString("playlistId") ?: "")
            }
            composable(
                route = "mbrowse/{playlistId}/{type}?catId={catId}",
                arguments = listOf(
                    navArgument("catId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { bs ->
                MobileBrowseScreen(
                    nav = nav,
                    playlistId = bs.arguments?.getString("playlistId") ?: "",
                    type = bs.arguments?.getString("type") ?: "movie",
                    initialCategoryId = bs.arguments?.getString("catId"),
                )
            }
            composable("msearch/{playlistId}") { bs ->
                MobileSearchScreen(nav, bs.arguments?.getString("playlistId") ?: "")
            }
            composable(
                route = "mseries/{playlistId}/{seriesId}/{name}?poster={poster}",
                arguments = listOf(
                    navArgument("poster") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { bs ->
                MobileSeriesDetailScreen(
                    nav = nav,
                    playlistId = bs.arguments?.getString("playlistId") ?: "",
                    seriesId = bs.arguments?.getString("seriesId") ?: "",
                    seriesName = Uri.decode(bs.arguments?.getString("name") ?: ""),
                    posterUrl = bs.arguments?.getString("poster")?.let(Uri::decode),
                )
            }
            composable("mplayer/{playlistId}/{streamUrl}/{channelName}/{isLive}") { bs ->
                MobilePlayerScreen(
                    nav = nav,
                    playlistId = bs.arguments?.getString("playlistId") ?: "",
                    streamUrl = bs.arguments?.getString("streamUrl") ?: "",
                    channelName = bs.arguments?.getString("channelName") ?: "",
                    isLive = bs.arguments?.getString("isLive") == "true",
                )
            }
        }

        MobileUpdateCheckHost()
    }
}

@Composable
private fun MobileUpdateCheckHost() {
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

/** Build a mobile player deep-link. Matches MobileApp's route template. */
fun mobilePlayerRoute(playlistId: String, streamUrl: String, channelName: String, isLive: Boolean): String =
    "mplayer/$playlistId/${Uri.encode(streamUrl)}/${Uri.encode(channelName)}/$isLive"

/** Build a mobile series-detail deep-link. */
fun mobileSeriesRoute(playlistId: String, seriesId: String, name: String, poster: String?): String {
    val base = "mseries/$playlistId/$seriesId/${Uri.encode(name)}"
    return if (poster.isNullOrBlank()) base else "$base?poster=${Uri.encode(poster)}"
}

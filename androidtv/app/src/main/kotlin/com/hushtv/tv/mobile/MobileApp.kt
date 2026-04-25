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
import com.hushtv.tv.ui.requests.RequestNotificationHost
import com.hushtv.tv.ui.requests.WatchTarget
import com.hushtv.tv.ui.theme.BgBlack
import com.hushtv.tv.update.UpdateDialog
import com.hushtv.tv.update.UpdateManager
import com.hushtv.tv.update.VersionInfo
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
        // Use "menu/" as the authenticated start — same route TVHomeScreen &
        // TVAddAccountScreen navigate to after login, so all three entry
        // paths converge on the same MobileShell.
        if (id != null && PlaylistStore.find(ctx, id) != null) "menu/$id" else "home"
    }

    Surface(
        modifier = Modifier.fillMaxSize().background(BgBlack),
        color = BgBlack,
    ) {
        NavHost(navController = nav, startDestination = startDestination) {
            composable("home") { MobileProfilePickerScreen(nav) }
            composable("add") { MobileAddAccountScreen(nav) }
            // Mobile-native profile & add screens both navigate to "menu/{id}"
            // after login. Route that to the mobile shell.
            composable("menu/{playlistId}") { bs ->
                MobileShell(nav, bs.arguments?.getString("playlistId") ?: "")
            }
            composable("shell/{playlistId}") { bs ->
                MobileShell(nav, bs.arguments?.getString("playlistId") ?: "")
            }
            composable("settings/{playlistId}") { bs ->
                // Mobile settings has no dedicated route yet; re-use the shell
                // so the user lands on the Settings tab via the bottom nav.
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
            composable("mdiag") { MobileDiagnosticsScreen(nav) }
            composable("mspeed") { MobileSpeedTestScreen(nav) }
            composable("mrequests") { com.hushtv.tv.ui.requests.MobileMyRequestsScreen(nav) }
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
            composable(
                route = "mcollection/{playlistId}/{collectionId}/{name}",
            ) { bs ->
                MobileCollectionDetailScreen(
                    nav = nav,
                    playlistId = bs.arguments?.getString("playlistId") ?: "",
                    tmdbCollectionId = bs.arguments?.getString("collectionId")?.toIntOrNull() ?: 0,
                    collectionName = Uri.decode(bs.arguments?.getString("name") ?: ""),
                )
            }
            composable(
                route = "mplayer/{playlistId}/{streamUrl}/{channelName}/{isLive}?catId={catId}&vodId={vodId}&vodKind={vodKind}&vodPoster={vodPoster}",
                arguments = listOf(
                    navArgument("catId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("vodId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("vodKind") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("vodPoster") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { bs ->
                MobilePlayerScreen(
                    nav = nav,
                    playlistId = bs.arguments?.getString("playlistId") ?: "",
                    streamUrl = bs.arguments?.getString("streamUrl") ?: "",
                    channelName = bs.arguments?.getString("channelName") ?: "",
                    isLive = bs.arguments?.getString("isLive") == "true",
                    liveCategoryId = bs.arguments?.getString("catId"),
                    vodStreamId = bs.arguments?.getString("vodId")?.toIntOrNull(),
                    vodKind = bs.arguments?.getString("vodKind"),
                    vodPoster = bs.arguments?.getString("vodPoster")?.let(Uri::decode),
                )
            }
        }

        MobileUpdateCheckHost()

        // In-app banner that polls the request gateway and lets the
        // user one-tap into a movie/series that just got fulfilled.
        // Reads the active playlist from LastProfileStore so we can
        // resolve titles to streamIds without prop-drilling.
        val requestPlaylistId = remember {
            LastProfileStore.load(ctx)?.takeIf { PlaylistStore.find(ctx, it) != null }
        }
        RequestNotificationHost(
            playlistId = requestPlaylistId,
            onWatchNow = { target ->
                val pid = requestPlaylistId ?: return@RequestNotificationHost
                when (target) {
                    is WatchTarget.Movie -> {
                        val playlist = PlaylistStore.find(ctx, pid)
                        if (playlist != null) {
                            val url = com.hushtv.tv.data.XtreamApi.movieUrl(
                                playlist.host, playlist.username, playlist.password,
                                target.streamId, null,
                            )
                            nav.navigate(
                                mobilePlayerRoute(
                                    playlistId = pid,
                                    streamUrl = url,
                                    channelName = target.title,
                                    isLive = false,
                                    vodStreamId = target.streamId,
                                    vodKind = "movie",
                                    vodPoster = null,
                                ),
                            )
                        } else {
                            nav.navigate("mrequests")
                        }
                    }
                    is WatchTarget.Series -> nav.navigate(
                        mobileSeriesRoute(
                            playlistId = pid,
                            seriesId = target.seriesId.toString(),
                            name = target.title,
                            poster = target.poster,
                        ),
                    )
                    WatchTarget.NotFound -> nav.navigate("mrequests")
                }
            },
        )
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

/** Build a mobile player deep-link. Matches MobileApp's route template.
 *  [liveCategoryId] is optional — supplied when jumping into a live channel
 *  so the player can fetch siblings for prev/next-channel controls.
 *  [vodStreamId] / [vodKind] / [vodPoster] are optional — supplied for
 *  movies and series episodes so the player can save + resume progress
 *  via WatchProgressStore. */
fun mobilePlayerRoute(
    playlistId: String,
    streamUrl: String,
    channelName: String,
    isLive: Boolean,
    liveCategoryId: String? = null,
    vodStreamId: Int? = null,
    vodKind: String? = null,
    vodPoster: String? = null,
): String {
    val base = "mplayer/$playlistId/${Uri.encode(streamUrl)}/${Uri.encode(channelName)}/$isLive"
    val q = buildList {
        if (isLive && !liveCategoryId.isNullOrBlank())
            add("catId=${Uri.encode(liveCategoryId)}")
        if (!isLive && vodStreamId != null && vodStreamId > 0)
            add("vodId=$vodStreamId")
        if (!isLive && !vodKind.isNullOrBlank())
            add("vodKind=${Uri.encode(vodKind)}")
        if (!isLive && !vodPoster.isNullOrBlank())
            add("vodPoster=${Uri.encode(vodPoster)}")
    }
    return if (q.isEmpty()) base else "$base?" + q.joinToString("&")
}

/** Build a mobile series-detail deep-link. */
fun mobileSeriesRoute(playlistId: String, seriesId: String, name: String, poster: String?): String {
    val base = "mseries/$playlistId/$seriesId/${Uri.encode(name)}"
    return if (poster.isNullOrBlank()) base else "$base?poster=${Uri.encode(poster)}"
}

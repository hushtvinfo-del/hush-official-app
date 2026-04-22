package com.hushtv.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hushtv.tv.ui.screens.TVAddAccountScreen
import com.hushtv.tv.ui.screens.TVBrowseScreen
import com.hushtv.tv.ui.screens.TVHomeScreen
import com.hushtv.tv.ui.screens.TVMainMenuScreen
import com.hushtv.tv.ui.screens.TVPlayerScreen
import com.hushtv.tv.ui.screens.TVSeriesDetailScreen
import com.hushtv.tv.ui.theme.HushTVTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HushTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    color = Color.Black
                ) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") { TVHomeScreen(nav) }
                        composable("add") { TVAddAccountScreen(nav) }
                        composable("menu/{playlistId}") { bs ->
                            TVMainMenuScreen(nav, bs.arguments?.getString("playlistId") ?: "")
                        }
                        composable("browse/{playlistId}/{type}") { bs ->
                            TVBrowseScreen(
                                nav,
                                bs.arguments?.getString("playlistId") ?: "",
                                bs.arguments?.getString("type") ?: "live"
                            )
                        }
                        composable("series/{playlistId}/{seriesId}/{seriesName}") { bs ->
                            TVSeriesDetailScreen(
                                nav,
                                bs.arguments?.getString("playlistId") ?: "",
                                bs.arguments?.getString("seriesId") ?: "",
                                bs.arguments?.getString("seriesName") ?: ""
                            )
                        }
                        composable("player/{streamUrl}/{channelName}/{isLive}") { bs ->
                            TVPlayerScreen(
                                nav,
                                bs.arguments?.getString("streamUrl") ?: "",
                                bs.arguments?.getString("channelName") ?: "",
                                bs.arguments?.getString("isLive") == "true"
                            )
                        }
                    }
                }
            }
        }
    }
}

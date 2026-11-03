package com.ontik.countai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ontik.countai.ui.screens.HistoryScreen
import com.ontik.countai.ui.screens.LiveCountingScreen
import com.ontik.countai.ui.screens.PhotoCountingScreen
import com.ontik.countai.ui.screens.SettingsScreen
import com.ontik.countai.ui.viewmodel.CountViewModel

object Destinations {
    const val LIVE = "live_counting"
    const val PHOTO = "photo_counting"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@Composable
fun OntikNavGraph(viewModel: CountViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Destinations.LIVE
    ) {
        composable(Destinations.LIVE) {
            LiveCountingScreen(
                viewModel = viewModel,
                onNavigateToPhoto = { navController.navigate(Destinations.PHOTO) },
                onNavigateToHistory = { navController.navigate(Destinations.HISTORY) },
                onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) }
            )
        }
        composable(Destinations.PHOTO) {
            PhotoCountingScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) }
            )
        }
        composable(Destinations.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

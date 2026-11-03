package com.ontik.countai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ontik.countai.ui.screens.AuthLoginScreen
import com.ontik.countai.ui.screens.AuthSignUpScreen
import com.ontik.countai.ui.screens.HistoryScreen
import com.ontik.countai.ui.screens.HomeDashboardScreen
import com.ontik.countai.ui.screens.LegalDocumentScreen
import com.ontik.countai.ui.screens.LiveCountingScreen
import com.ontik.countai.ui.screens.PaywallScreen
import com.ontik.countai.ui.screens.ProfileScreen
import com.ontik.countai.ui.screens.SettingsScreen
import com.ontik.countai.ui.viewmodel.CountViewModel

object Destinations {
    const val LIVE = "live_counting"
    const val HOME_DASHBOARD = "home_dashboard"
    const val LOGIN = "auth_login"
    const val SIGNUP = "auth_signup"
    const val PROFILE = "user_profile"
    const val PAYWALL = "pro_paywall"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PRIVACY_POLICY = "privacy_policy"
    const val TERMS_OF_SERVICE = "terms_of_service"
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
                onNavigateToHome = { navController.navigate(Destinations.HOME_DASHBOARD) },
                onNavigateToHistory = { navController.navigate(Destinations.HISTORY) },
                onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                onNavigateToPaywall = { navController.navigate(Destinations.PAYWALL) },
                onNavigateToProfile = { navController.navigate(Destinations.PROFILE) }
            )
        }
        composable(Destinations.HOME_DASHBOARD) {
            HomeDashboardScreen(
                viewModel = viewModel,
                onStartCounting = { navController.popBackStack(Destinations.LIVE, inclusive = false) },
                onNavigateToPaywall = { navController.navigate(Destinations.PAYWALL) },
                onNavigateToProfile = { navController.navigate(Destinations.PROFILE) },
                onNavigateToLogin = { navController.navigate(Destinations.LOGIN) },
                onNavigateToHistory = { navController.navigate(Destinations.HISTORY) },
                onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) }
            )
        }
        composable(Destinations.LOGIN) {
            AuthLoginScreen(
                viewModel = viewModel,
                onNavigateToSignUp = { navController.navigate(Destinations.SIGNUP) },
                onContinueAsGuestOrSuccess = { navController.popBackStack() }
            )
        }
        composable(Destinations.SIGNUP) {
            AuthSignUpScreen(
                viewModel = viewModel,
                onBackToLogin = { navController.popBackStack() },
                onSignUpSuccess = {
                    navController.popBackStack(Destinations.LIVE, inclusive = false)
                },
                onOpenTerms = { navController.navigate(Destinations.TERMS_OF_SERVICE) },
                onOpenPrivacy = { navController.navigate(Destinations.PRIVACY_POLICY) }
            )
        }
        composable(Destinations.PROFILE) {
            ProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.navigate(Destinations.LOGIN) },
                onNavigateToPaywall = { navController.navigate(Destinations.PAYWALL) }
            )
        }
        composable(Destinations.PAYWALL) {
            PaywallScreen(
                viewModel = viewModel,
                onClose = { navController.popBackStack() },
                onOpenTerms = { navController.navigate(Destinations.TERMS_OF_SERVICE) },
                onOpenPrivacy = { navController.navigate(Destinations.PRIVACY_POLICY) }
            )
        }
        composable(Destinations.HISTORY) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPaywall = { navController.navigate(Destinations.PAYWALL) }
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPaywall = { navController.navigate(Destinations.PAYWALL) },
                onNavigateToProfile = { navController.navigate(Destinations.PROFILE) },
                onOpenPrivacy = { navController.navigate(Destinations.PRIVACY_POLICY) },
                onOpenTerms = { navController.navigate(Destinations.TERMS_OF_SERVICE) }
            )
        }
        composable(Destinations.PRIVACY_POLICY) {
            LegalDocumentScreen(
                isPrivacyPolicy = true,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Destinations.TERMS_OF_SERVICE) {
            LegalDocumentScreen(
                isPrivacyPolicy = false,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

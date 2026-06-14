package com.estrelladebelen.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.estrelladebelen.app.R
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.estrelladebelen.app.ui.screens.SplashScreen
import com.estrelladebelen.app.ui.screens.auth.LoginScreen
import com.estrelladebelen.app.ui.screens.auth.RegisterScreen
import com.estrelladebelen.app.ui.screens.home.HomeScreen
import com.estrelladebelen.app.ui.screens.player.PlayerScreen
import com.estrelladebelen.app.ui.screens.player.PlayerViewModel
import com.estrelladebelen.app.ui.screens.paywall.PaywallScreen
import com.estrelladebelen.app.ui.screens.profile.DownloadsScreen
import com.estrelladebelen.app.ui.screens.profile.FavoritesScreen
import com.estrelladebelen.app.ui.screens.profile.ProfileScreen
import com.estrelladebelen.app.ui.screens.profile.ProfileViewModel

private val bottomNavItems = listOf(
    Screen.Home    to (R.string.nav_home    to Icons.Filled.Home),
    Screen.Profile to (R.string.nav_profile to Icons.Filled.Settings)
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    val playerViewModel: PlayerViewModel   = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()

    val userProfile by profileViewModel.userProfile.collectAsStateWithLifecycle()

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val showBottomBar = currentRoute in listOf(Screen.Home.route, Screen.Profile.route)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDest = currentBackStack?.destination
                    bottomNavItems.forEach { (screen, meta) ->
                        val (labelRes, icon) = meta
                        NavigationBarItem(
                            selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                            label = { Text(stringResource(labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // PlayerScreen draws edge-to-edge (pure black + GL animation).
        // Passing the Scaffold inset padding would expose the theme background
        // as colored strips at top and bottom in light mode.
        val effectivePadding = if (currentRoute == Screen.Player.route)
            PaddingValues(0.dp)
        else
            innerPadding

        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(effectivePadding),
            enterTransition    = { fadeIn(tween(300)) },
            exitTransition     = { fadeOut(tween(300)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition  = { fadeOut(tween(300)) }
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onReady = {
                        val destination = if (Firebase.auth.currentUser != null)
                            Screen.Home.route
                        else
                            Screen.Login.route
                        navController.navigate(destination) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.navigate(Screen.Register.route) }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    userName    = userProfile?.displayName ?: "",
                    favorites   = userProfile?.favorites ?: emptyList(),
                    onMeditationClick = { id ->
                        navController.navigate(Screen.Player.createRoute(id))
                    },
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                    onFavoriteClick = { id -> profileViewModel.toggleFavorite(id) }
                )
            }

            composable(
                Screen.Player.route,
                enterTransition    = { fadeIn(tween(600)) },
                exitTransition     = { fadeOut(tween(800)) },
                popEnterTransition = { fadeIn(tween(600)) },
                popExitTransition  = { fadeOut(tween(800)) }
            ) { backStack ->
                val meditationId = backStack.arguments?.getString("meditationId") ?: return@composable
                val isFavorite = meditationId in (userProfile?.favorites ?: emptyList())
                PlayerScreen(
                    meditationId     = meditationId,
                    onBack           = { navController.popBackStack() },
                    viewModel        = playerViewModel,
                    isFavorite       = isFavorite,
                    onFavoriteToggle = { profileViewModel.toggleFavorite(meditationId) }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onSignOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onFavoritesClick = { navController.navigate(Screen.Favorites.route) },
                    onDownloadsClick = { navController.navigate(Screen.Downloads.route) },
                    onSubscriptionClick = { navController.navigate(Screen.Paywall.route) },
                    viewModel = profileViewModel
                )
            }

            composable(Screen.Paywall.route) {
                PaywallScreen(onDismiss = { navController.popBackStack() })
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onBack      = { navController.popBackStack() },
                    onPlayClick = { id -> navController.navigate(Screen.Player.createRoute(id)) },
                    viewModel   = profileViewModel
                )
            }

            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onBack      = { navController.popBackStack() },
                    onPlayClick = { id -> navController.navigate(Screen.Player.createRoute(id)) },
                    viewModel   = profileViewModel
                )
            }
        }
    }
}

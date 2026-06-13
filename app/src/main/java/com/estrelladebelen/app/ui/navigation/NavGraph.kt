package com.estrelladebelen.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.estrelladebelen.app.R
import com.estrelladebelen.app.ui.components.MiniPlayer
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.estrelladebelen.app.ui.screens.SplashScreen
import com.estrelladebelen.app.ui.screens.auth.LoginScreen
import com.estrelladebelen.app.ui.screens.auth.RegisterScreen
import com.estrelladebelen.app.ui.screens.home.HomeScreen
import com.estrelladebelen.app.ui.screens.player.PlayerScreen
import com.estrelladebelen.app.ui.screens.player.PlayerViewModel
import com.estrelladebelen.app.ui.screens.profile.ProfileScreen
import com.estrelladebelen.app.ui.screens.profile.ProfileViewModel
import com.estrelladebelen.app.ui.theme.LavenderBackground

private val bottomNavItems = listOf(
    Screen.Home    to (R.string.nav_home    to Icons.Filled.Home),
    Screen.Profile to (R.string.nav_profile to Icons.Filled.Person)
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    // Shared ViewModels scoped to the NavGraph
    val playerViewModel: PlayerViewModel     = viewModel()
    val profileViewModel: ProfileViewModel   = viewModel()

    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val userProfile by profileViewModel.userProfile.collectAsStateWithLifecycle()

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val showBottomBar = currentRoute in listOf(Screen.Home.route, Screen.Profile.route)
    val showMiniPlayer = playerState.meditation != null && currentRoute != Screen.Player.route

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
                                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer,
                                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.fillMaxSize()
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
                    onFavoriteClick = { /* TODO: toggle via ProfileViewModel */ }
                )
            }

            composable(Screen.Player.route) { backStack ->
                val meditationId = backStack.arguments?.getString("meditationId") ?: return@composable
                PlayerScreen(
                    meditationId = meditationId,
                    onBack = { navController.popBackStack() },
                    viewModel = playerViewModel
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onSignOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    viewModel = profileViewModel
                )
            }
        }

        // Mini-player floating above bottom nav (shown when audio is active outside PlayerScreen)
        if (showMiniPlayer && showBottomBar) {
            MiniPlayer(
                state = playerState,
                onPlayerClick = {
                    playerState.meditation?.let { m ->
                        navController.navigate(Screen.Player.createRoute(m.id))
                    }
                },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            )
        }
        } // end Box
    }
}

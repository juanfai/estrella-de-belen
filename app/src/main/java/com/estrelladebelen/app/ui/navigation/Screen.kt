package com.estrelladebelen.app.ui.navigation

sealed class Screen(val route: String) {
    object Splash   : Screen("splash")
    object Login    : Screen("login")
    object Register : Screen("register")
    object Home     : Screen("home")
    object Profile  : Screen("profile")
    object Favorites  : Screen("favorites")
    object Downloads  : Screen("downloads")

    object Player : Screen("player/{meditationId}") {
        fun createRoute(meditationId: String) = "player/$meditationId"
    }
}

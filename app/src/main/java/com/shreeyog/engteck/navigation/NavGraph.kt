package com.shreeyog.engteck.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shreeyog.engteck.screens.HomeScreen
import com.shreeyog.engteck.screens.JoinCodeScreen
import com.shreeyog.engteck.screens.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val JOIN_CODE = "join_code"
    const val HOME = "home"
}

// initialJoinCode comes from a tapped class-share link (see MainActivity) —
// when present, the app opens straight into JoinCodeScreen with that code
// already filled in and auto-submitted instead of the normal Home start.
@Composable
fun EngTeckNavGraph(initialJoinCode: String? = null) {
    val navController: NavHostController = rememberNavController()
    val teacherName = remember { mutableStateOf("Teacher") }

    val startDestination = if (!initialJoinCode.isNullOrBlank()) Routes.JOIN_CODE else Routes.HOME

    // NavHost's startDestination is only applied when the graph is first
    // built — if MainActivity is already running (singleTask) and a new
    // link arrives via onNewIntent, initialJoinCode changes AFTER this
    // graph already exists, so startDestination alone can't react to it.
    // This effect re-runs whenever initialJoinCode changes and explicitly
    // navigates, covering that already-running case.
    LaunchedEffect(initialJoinCode) {
        if (!initialJoinCode.isNullOrBlank()) {
            navController.navigate(Routes.JOIN_CODE) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // App otherwise still opens straight into Home — no splash, no manual
    // Join Code step — unchanged from before.
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                navController.navigate(Routes.JOIN_CODE) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.JOIN_CODE) {
            JoinCodeScreen(
                prefillCode = initialJoinCode,
                onValidCode = { _, name ->
                    teacherName.value = name
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.JOIN_CODE) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(teacherName = teacherName.value)
        }
    }
}

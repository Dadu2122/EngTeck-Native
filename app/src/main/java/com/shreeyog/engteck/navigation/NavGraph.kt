package com.shreeyog.engteck.navigation

import androidx.compose.runtime.Composable
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

@Composable
fun EngTeckNavGraph() {
    val navController: NavHostController = rememberNavController()
    val teacherName = remember { mutableStateOf("Teacher") }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                navController.navigate(Routes.JOIN_CODE) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.JOIN_CODE) {
            JoinCodeScreen(onValidCode = { _, name ->
                teacherName.value = name
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.JOIN_CODE) { inclusive = true }
                }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(teacherName = teacherName.value)
        }
    }
}

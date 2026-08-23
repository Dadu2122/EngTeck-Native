package com.shreeyog.engteck.screens

import androidx.compose.runtime.*

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Opens straight into the app — no delay, no animation.
    LaunchedEffect(Unit) {
        onFinished()
    }
}

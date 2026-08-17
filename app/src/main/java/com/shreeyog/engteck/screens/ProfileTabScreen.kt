package com.shreeyog.engteck.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileTabScreen() {
    var showAdminLogin by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }
    var adminName by remember { mutableStateOf("") }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    when {
        isAdmin -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { isAdmin = false }) { Text("‹ Logout") }
                AdminPanelPlaceholder(adminName)
            }
        }
        showAdminLogin -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { showAdminLogin = false }) { Text("‹ Back") }
                AdminLoginScreen(onLoginSuccess = { _, name ->
                    adminName = name
                    isAdmin = true
                    showAdminLogin = false
                })
            }
        }
        else -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("👤", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text("Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                Spacer(Modifier.height(8.dp))
                Text(
                    "TGT / PGT / LT / GIC Lecturer Preparation",
                    fontSize = 12.sp,
                    color = Color(0xFF5B5F6B)
                )

                Spacer(Modifier.weight(1f))

                // Hidden admin access: tap this line 7 times within 3 seconds to open Admin Login.
                // Not visible to students as a real button — looks like plain app version text.
                Text(
                    "EngTeck v1.0",
                    fontSize = 11.sp,
                    color = Color(0xFF5B5F6B).copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        val now = System.currentTimeMillis()
                        tapCount = if (now - lastTapTime > 900) 1 else tapCount + 1
                        lastTapTime = now
                        if (tapCount >= 7) {
                            tapCount = 0
                            showAdminLogin = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminPanelPlaceholder(teacherName: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome, $teacherName 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(8.dp))
        Text("Admin Panel — content editing tools coming next", fontSize = 13.sp, color = Color(0xFF5B5F6B))
    }
}

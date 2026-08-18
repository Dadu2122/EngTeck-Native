package com.shreeyog.engteck.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                AdminPanelScreen()
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
private fun AdminPanelScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Admin Panel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(16.dp))
        AdminContentEditorCard()
        Spacer(Modifier.height(16.dp))
        AdminFreePdfSetsCard()
        Spacer(Modifier.height(16.dp))
        AdminPremiumPdfSetsCard()
        Spacer(Modifier.height(16.dp))
        AdminWritersCard()
        Spacer(Modifier.height(16.dp))
        AdminTopicSectionsCard()
        Spacer(Modifier.height(16.dp))
        AdminDailyPracticeCard()
        Spacer(Modifier.height(16.dp))
        AdminSelfAssessmentCard()
        Spacer(Modifier.height(16.dp))
        AdminVideosRecordingsCard()
        Spacer(Modifier.height(16.dp))
        AdminAddRegistrationCard()
        Spacer(Modifier.height(16.dp))
        AdminScoresManagerCard()
        Spacer(Modifier.height(16.dp))
        AdminMiniBookUploadCard()
        Spacer(Modifier.height(16.dp))
        AdminDataViewersCard()
        Spacer(Modifier.height(30.dp))
    }
}


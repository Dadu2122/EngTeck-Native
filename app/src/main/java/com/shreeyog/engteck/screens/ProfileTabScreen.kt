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
    var adminTeacherKey by remember { mutableStateOf("") }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    when {
        isAdmin -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { isAdmin = false }) { Text("‹ Logout") }
                AdminPanelScreen(teacherKey = adminTeacherKey, teacherName = adminName)
            }
        }
        showAdminLogin -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { showAdminLogin = false }) { Text("‹ Back") }
                AdminLoginScreen(onLoginSuccess = { key, name ->
                    adminTeacherKey = key
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
private fun AdminPanelScreen(teacherKey: String, teacherName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Admin Panel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(16.dp))

        AdminAccordionSection("Content Editor", "📝") { AdminContentEditorCard() }
        AdminAccordionSection("Live Class Control", "🔴") { AdminLiveClassCard(teacherKey = teacherKey, teacherName = teacherName) }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Live Class — Teachers", "👥") { AdminTeachersCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Free PDF Sets", "📄") { AdminFreePdfSetsCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Premium PDF Library", "📚") { AdminPremiumPdfSetsCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Writers & Works", "✍️") { AdminWritersCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Syllabus", "📘") { AdminSyllabusCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Topic Sections", "📖") { AdminTopicSectionsCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Daily Practice", "🔥") { AdminDailyPracticeCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Self Assessment", "🎯") { AdminSelfAssessmentCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Videos & Recordings", "🎬") { AdminVideosRecordingsCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Add Registration", "➕") { AdminAddRegistrationCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Scores Manager", "🏆") { AdminScoresManagerCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Special Note Books", "📕") { AdminMiniBookUploadCard() }
        Spacer(Modifier.height(10.dp))
        AdminAccordionSection("Data Viewer", "📊") { AdminDataViewersCard() }
        Spacer(Modifier.height(30.dp))
    }
}

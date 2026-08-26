package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

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
    // When true, the rest of the Admin Panel (every other accordion section)
    // is not composed at all — only the Live Class Control card is on screen.
    // This is what keeps scrolling confined to just the board/tools while a
    // class is running, instead of scrolling through the whole panel list.
    var liveClassOpen by remember { mutableStateOf(false) }

    if (liveClassOpen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            TextButton(
                onClick = { liveClassOpen = false },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) { Text("‹ Back to Admin Panel") }

            AdminLiveClassCard(
                teacherKey = teacherKey,
                teacherName = teacherName,
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Text("Admin Panel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D), modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))

        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Content Editor", "📝") { AdminContentEditorCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Teacher Profile Card", "🧑‍🏫") { AdminTeacherProfileCard() } }
        Spacer(Modifier.height(10.dp))
        // Tapping this now switches into the isolated full-screen Live Class
        // view above instead of expanding inline — see liveClassOpen.
        Box(Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
                    .clickable { liveClassOpen = true }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔴 Live Class Control", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                Text("▶ Open", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B6B79))
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Live Class — Teachers", "👥") { AdminTeachersCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Free PDF Sets", "📄") { AdminFreePdfSetsCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Premium PDF Library", "📚") { AdminPremiumPdfSetsCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Writers & Works", "✍️") { AdminWritersCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Syllabus", "📘") { AdminSyllabusCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Topic Sections", "📖") { AdminTopicSectionsCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Daily Practice", "🔥") { AdminDailyPracticeCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Self Assessment", "🎯") { AdminSelfAssessmentCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Videos & Recordings", "🎬") { AdminVideosRecordingsCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Add Registration", "➕") { AdminAddRegistrationCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Scores Manager", "🏆") { AdminScoresManagerCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Selected Aspirants Card", "🎓") { AdminSelectedAspirantsVisibilityCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Special Note Books", "📕") { AdminMiniBookUploadCard() } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 16.dp)) { AdminAccordionSection("Data Viewer", "📊") { AdminDataViewersCard() } }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AdminSelectedAspirantsVisibilityCard() {
    var isVisible by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("content").child("showSelectedAspirantsCard")
            .get()
            .addOnSuccessListener { snapshot ->
                isVisible = snapshot.getValue(Boolean::class.java) ?: true
                loaded = true
            }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Show on Home Screen",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF12203D)
                )
                Text(
                    if (isVisible) "Card is currently visible" else "Card is currently hidden",
                    fontSize = 11.sp,
                    color = Color(0xFF5B5F6B)
                )
            }
            Switch(
                checked = isVisible,
                enabled = loaded,
                onCheckedChange = { checked ->
                    isVisible = checked
                    FirebaseDatabase.getInstance().getReference("content")
                        .child("showSelectedAspirantsCard")
                        .setValue(checked)
                }
            )
        }
    }
}

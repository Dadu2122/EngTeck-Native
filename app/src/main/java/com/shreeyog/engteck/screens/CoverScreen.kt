package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

private val NavyC = Color(0xFF12203D)
private val NavyDeepC = Color(0xFF0B1730)
private val Gold = Color(0xFFD4A017)
private val GoldSoft = Color(0xFFF0D384)
private val Teal = Color(0xFF1B6B79)
private val Maroon = Color(0xFF7A2E3D)

data class CoverContent(
    val coachingLine1: String = "Shree English Classes",
    val tagline: String = "Guided prep for TGT · PGT · LT · GIC Lecturer aspirants, with self-taught video lectures.",
    val trustBadge: String = "500+ Aspirants Trained",
    val statStudents: String = "500+",
    val statSuccess: String = "92%",
    val statYears: String = "8+"
)

@Composable
fun CoverScreen(
    onProgressClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    onWatchDemoClick: () -> Unit = {}
) {
    var content by remember { mutableStateOf(CoverContent()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("content")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    content = CoverContent(
                        coachingLine1 = snapshot.child("coachingLine1").getValue(String::class.java) ?: content.coachingLine1,
                        tagline = snapshot.child("tagline").getValue(String::class.java) ?: content.tagline,
                        trustBadge = snapshot.child("trustBadge").getValue(String::class.java) ?: content.trustBadge,
                        statStudents = snapshot.child("statStudents").getValue(String::class.java) ?: content.statStudents,
                        statSuccess = snapshot.child("statSuccess").getValue(String::class.java) ?: content.statSuccess,
                        statYears = snapshot.child("statYears").getValue(String::class.java) ?: content.statYears
                    )
                }
                loaded = true
            }
            .addOnFailureListener { loaded = true }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(NavyC, NavyDeepC, Color(0xFF060D1C)),
                )
            )
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                "COACHING INSTITUTE",
                color = GoldSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("ENGLISH", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Gold.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
                    .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text("★ ", color = Gold, fontSize = 12.sp)
                EditableText("trustBadge", content.trustBadge, { content = content.copy(trustBadge = it) }) { v ->
                    Text(if (loaded) v else "", color = GoldSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                CircleIcon("☀️", Color.White.copy(alpha = 0.08f))
                QuickEditToggleCap()
                CircleIcon("🌙", Color.White.copy(alpha = 0.08f))
            }

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Gold, RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(vertical = 16.dp, horizontal = 14.dp)
            ) {
                EditableText("coachingLine1", content.coachingLine1, { content = content.copy(coachingLine1 = it) }) { v ->
                    Text(
                        v,
                        color = Gold,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            EditableText("tagline", content.tagline, { content = content.copy(tagline = it) }) { v ->
                Text(
                    v,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onRegisterClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = NavyDeepC),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Register Now", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }

                OutlinedButton(
                    onClick = onWatchDemoClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("▶  Watch Demo", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatBox("statStudents", if (loaded) content.statStudents else "", "STUDENTS", { content = content.copy(statStudents = it) }, Modifier.weight(1f))
                StatBox("statSuccess", if (loaded) content.statSuccess else "", "SUCCESS RATE", { content = content.copy(statSuccess = it) }, Modifier.weight(1f))
                StatBox("statYears", if (loaded) content.statYears else "", "YEARS", { content = content.copy(statYears = it) }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("TGT / PGT")
                Pill("LT Grade")
                Pill("UPPSC / UPHESC")
            }

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Maroon)
                    .clickable { onProgressClick() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("📊 Total Registrations and Progress Analytics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Coaching Reg. No: UDYAM-UK-09-0013602",
                color = Gold,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CircleIcon(emoji: String, bg: Color) {
    Box(
        modifier = Modifier.size(38.dp).clip(CircleShape).background(bg).border(1.5.dp, Color.White.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 15.sp)
    }
}

@Composable
private fun StatBox(fieldKey: String, value: String, label: String, onSaved: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EditableText(fieldKey, value, onSaved) { v ->
                Text(v, color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(100.dp))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp)
    }
}

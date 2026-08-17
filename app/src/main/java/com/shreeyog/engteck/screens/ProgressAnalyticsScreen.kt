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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

private val PA_CATS = listOf("tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC")

private val CAT_TAB_COLORS = mapOf(
    "tgt" to listOf(Color(0xFF1B6B79), Color(0xFF124F59)),
    "pgt" to listOf(Color(0xFF3B6EA8), Color(0xFF1F4066)),
    "lt" to listOf(Color(0xFF7A2E3D), Color(0xFF4A1B25)),
    "gic" to listOf(Color(0xFFD4A017), Color(0xFF93700E))
)

data class ScorerEntry(val name: String, val pct: Int)
data class StudentEntry(val name: String, val category: String, val isLive: Boolean, val amount: Int)

@Composable
fun ProgressAnalyticsScreen(onClose: () -> Unit) {
    var activeCat by remember { mutableStateOf("tgt") }
    var activeView by remember { mutableStateOf("scorers") }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF0B1730), Color(0xFF12203D), Color(0xFF1B6B79)))
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📊 Progress Analytics", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("✕", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable { onClose() })
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                PA_CATS.forEach { (key, label) ->
                    val active = activeCat == key
                    val colors = CAT_TAB_COLORS[key]!!
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (active) Brush.linearGradient(colors) else Brush.linearGradient(listOf(Color(0xFFF5F3EC), Color(0xFFF5F3EC))))
                            .border(1.5.dp, if (active) colors[0] else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                            .clickable { activeCat = key }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(100.dp))
                        .background(
                            if (activeView == "scorers") Brush.linearGradient(listOf(Color(0xFF2FA85A), Color(0xFF1F7A3D)))
                            else Brush.linearGradient(listOf(Color(0xFFF5F3EC), Color(0xFFF5F3EC)))
                        )
                        .border(1.5.dp, if (activeView == "scorers") Color(0xFF1F7A3D) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                        .clickable { activeView = "scorers" }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🏆 Top Scorers", color = if (activeView == "scorers") Color.White else Color(0xFF5B5F6B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(100.dp))
                        .background(
                            if (activeView == "students") Brush.linearGradient(listOf(Color(0xFF2FA85A), Color(0xFF1F7A3D)))
                            else Brush.linearGradient(listOf(Color(0xFFF5F3EC), Color(0xFFF5F3EC)))
                        )
                        .border(1.5.dp, if (activeView == "students") Color(0xFF1F7A3D) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                        .clickable { activeView = "students" }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📋 Registered Students", color = if (activeView == "students") Color.White else Color(0xFF5B5F6B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))

            if (activeView == "scorers") {
                TopScorersList(activeCat)
            } else {
                RegisteredStudentsList(activeCat)
            }
        }
    }
}

@Composable
private fun TopScorersList(catKey: String) {
    var loading by remember(catKey) { mutableStateOf(true) }
    var scorers by remember(catKey) { mutableStateOf<List<ScorerEntry>>(emptyList()) }

    LaunchedEffect(catKey) {
        loading = true
        FirebaseDatabase.getInstance().getReference("saScores").child(catKey)
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                scorers = snapshot.children.mapNotNull { s ->
                    val name = s.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    val pct = s.child("pct").getValue(Long::class.java)?.toInt()
                        ?: run {
                            val score = s.child("score").getValue(Long::class.java) ?: 0L
                            val total = s.child("total").getValue(Long::class.java) ?: 1L
                            if (total > 0) ((score * 100) / total).toInt() else 0
                        }
                    ScorerEntry(name, pct)
                }.sortedByDescending { it.pct }.take(10)
            }
            .addOnFailureListener { loading = false }
    }

    if (loading) {
        Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        }
    } else if (scorers.isEmpty()) {
        Text(
            "No one has taken a Self Assessment in this category yet.",
            fontSize = 12.5.sp,
            color = Color(0xFF5B5F6B),
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    } else {
        Column {
            scorers.forEachIndexed { index, s ->
                val rank = index + 1
                val bgBrush = when (rank) {
                    1 -> Brush.verticalGradient(listOf(Color(0xFFF0C64A), Color(0xFFD4A017), Color(0xFF93700E)))
                    2 -> Brush.verticalGradient(listOf(Color(0xFFD9DEE5), Color(0xFFA8B0BC), Color(0xFF707886)))
                    3 -> Brush.verticalGradient(listOf(Color(0xFFD79A6A), Color(0xFFB06A34), Color(0xFF7A461F)))
                    else -> Brush.verticalGradient(listOf(Color(0xFF2C3E5A), Color(0xFF1B2A42), Color(0xFF0F1B2E)))
                }
                val rightTextColor = when (rank) {
                    1 -> Color(0xFF7A2E3D)
                    2 -> Color(0xFF12203D)
                    3 -> Color(0xFFFFF3D2)
                    else -> Color.White
                }
                val ordinal = when {
                    rank % 100 in 11..13 -> "th"
                    rank % 10 == 1 -> "st"
                    rank % 10 == 2 -> "nd"
                    rank % 10 == 3 -> "rd"
                    else -> "th"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgBrush)
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$rank", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(s.name, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("$rank$ordinal-position", color = rightTextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RegisteredStudentsList(catKey: String) {
    var loading by remember(catKey) { mutableStateOf(true) }
    var students by remember(catKey) { mutableStateOf<List<StudentEntry>>(emptyList()) }

    LaunchedEffect(catKey) {
        loading = true
        FirebaseDatabase.getInstance().getReference("registrations")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                students = snapshot.children.mapNotNull { r ->
                    val planCategory = r.child("planCategory").getValue(String::class.java) ?: return@mapNotNull null
                    if (planCategory != catKey) return@mapNotNull null
                    val name = r.child("nickname").getValue(String::class.java)
                        ?: r.child("name").getValue(String::class.java) ?: "-"
                    val planType = r.child("planType").getValue(String::class.java) ?: ""
                    val isLive = planType.startsWith("Live Class") && !planType.startsWith("No Live")
                    val amount = r.child("planAmount").getValue(Long::class.java)?.toInt() ?: 0
                    StudentEntry(name, planCategory, isLive, amount)
                }
            }
            .addOnFailureListener { loading = false }
    }

    if (loading) {
        Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        }
    } else if (students.isEmpty()) {
        Text(
            "No registered students found in this category.",
            fontSize = 12.5.sp,
            color = Color(0xFF5B5F6B),
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    } else {
        Column {
            students.forEach { s ->
                val initial = s.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF1B6B79), Color(0xFF12203D)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initial, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE8EEF7), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(s.category.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B6EA8))
                            }
                            Box(
                                modifier = Modifier
                                    .background(if (s.isLive) Color(0xFFE3F5E9) else Color(0xFFF0EEE7), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    if (s.isLive) "Live" else "No Live",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (s.isLive) Color(0xFF1F7A3D) else Color(0xFF5B5F6B)
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFDF6E3), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("₹${s.amount}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD4A017))
                    }
                }
            }
        }
    }
}

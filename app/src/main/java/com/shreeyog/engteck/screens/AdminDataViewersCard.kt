package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

data class AdminEntry(val key: String, val lines: List<String>)
data class AdminStudentEntry(val name: String, val category: String, val isLive: Boolean, val amount: Int, val mobile: String)

private val ADMIN_CATS = listOf("tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC")

@Composable
fun AdminDataViewersCard() {
    var activeTab by remember { mutableStateOf("registrations") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Data Viewer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("registrations" to "Registrations", "inquiries" to "Inquiries", "helpFeedback" to "Feedback").forEach { (key, label) ->
                val active = activeTab == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC))
                        .clickable { activeTab = key }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (activeTab == "registrations") {
            AdminRegistrationsCardView()
        } else {
            AdminEntryList(firebasePath = activeTab)
        }
    }
}

@Composable
private fun AdminRegistrationsCardView() {
    var activeCat by remember { mutableStateOf("tgt") }
    var loading by remember { mutableStateOf(true) }
    var allStudents by remember { mutableStateOf<List<AdminStudentEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("registrations")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                allStudents = snapshot.children.mapNotNull { r ->
                    val category = r.child("planCategory").getValue(String::class.java) ?: return@mapNotNull null
                    val name = r.child("nickname").getValue(String::class.java)
                        ?: r.child("name").getValue(String::class.java) ?: "-"
                    val planType = r.child("planType").getValue(String::class.java) ?: ""
                    val isLive = planType.startsWith("Live Class") && !planType.startsWith("No Live")
                    val amount = r.child("planAmount").getValue(Long::class.java)?.toInt() ?: 0
                    val mobile = r.child("mobile").getValue(String::class.java) ?: ""
                    AdminStudentEntry(name, category, isLive, amount, mobile)
                }
            }
            .addOnFailureListener { loading = false }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ADMIN_CATS.forEach { (key, label) ->
            val active = activeCat == key
            val count = allStudents.count { it.category.equals(key, ignoreCase = true) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC))
                    .border(1.5.dp, if (active) Color(0xFF1B6B79) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                    .clickable { activeCat = key }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$label ($count)", color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    val filtered = allStudents.filter { it.category.equals(activeCat, ignoreCase = true) }

    if (loading) {
        CircularProgressIndicator(color = Color(0xFF12203D))
    } else if (filtered.isEmpty()) {
        Text("No registered students in this category.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
    } else {
        Column(modifier = Modifier.heightIn(max = 420.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(filtered) { s ->
                    val initial = s.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF1B6B79), Color(0xFF12203D)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initial, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                            if (s.mobile.isNotEmpty()) {
                                Text(s.mobile, fontSize = 10.5.sp, color = Color(0xFF5B5F6B))
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE8EEF7), RoundedCornerShape(100.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(s.category.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B6EA8))
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (s.isLive) Color(0xFFE3F5E9) else Color(0xFFF0EEE7), RoundedCornerShape(100.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (s.isLive) "Live" else "No Live",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (s.isLive) Color(0xFF1F7A3D) else Color(0xFF5B5F6B)
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFDF6E3), RoundedCornerShape(10.dp))
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text("₹${s.amount}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD4A017))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminEntryList(firebasePath: String) {
    var loading by remember(firebasePath) { mutableStateOf(true) }
    var entries by remember(firebasePath) { mutableStateOf<List<AdminEntry>>(emptyList()) }

    LaunchedEffect(firebasePath) {
        loading = true
        FirebaseDatabase.getInstance().getReference(firebasePath)
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                entries = snapshot.children.map { child ->
                    val lines = mutableListOf<String>()
                    child.children.forEach { field ->
                        if (field.key != "timestamp") {
                            val v = field.value?.toString() ?: ""
                            if (v.isNotBlank()) lines.add("${field.key}: $v")
                        }
                    }
                    AdminEntry(child.key ?: "", lines)
                }.reversed()
            }
            .addOnFailureListener { loading = false }
    }

    if (loading) {
        CircularProgressIndicator(color = Color(0xFF12203D))
    } else if (entries.isEmpty()) {
        Text("No entries yet.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
    } else {
        LazyColumn(modifier = Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    entry.lines.forEach { line ->
                        Text(line, fontSize = 11.5.sp, color = Color(0xFF1A1A1A))
                    }
                }
            }
        }
    }
}

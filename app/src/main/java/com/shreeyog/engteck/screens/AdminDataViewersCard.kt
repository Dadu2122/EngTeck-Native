package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

data class AdminEntry(val key: String, val lines: List<String>)

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

        AdminEntryList(firebasePath = activeTab)
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

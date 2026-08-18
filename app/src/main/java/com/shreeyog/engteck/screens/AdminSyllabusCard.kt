package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

// premiumContent/{catKey}/syllabus -> plain text, part of Premium Study Material.
private val SYLLABUS_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)

@Composable
fun AdminSyllabusCard() {
    var activeCat by remember { mutableStateOf("tgt") }
    var syllabus by remember(activeCat) { mutableStateOf("") }
    var loading by remember(activeCat) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(activeCat) {
        loading = true
        status = ""
        FirebaseDatabase.getInstance().getReference("premiumContent").child(activeCat).child("syllabus")
            .get()
            .addOnSuccessListener { s ->
                syllabus = s.getValue(String::class.java) ?: ""
                loading = false
            }
            .addOnFailureListener { loading = false }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("📘 Syllabus", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(4.dp))
        Text("Part of Premium Study Material — shown inside each category's PDF Library", fontSize = 11.sp, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(12.dp))

        SYLLABUS_CATS.chunked(4).forEach { rowCats ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                rowCats.forEach { (key, label) ->
                    val active = activeCat == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC), RoundedCornerShape(100.dp))
                            .border(1.5.dp, if (active) Color(0xFF1B6B79) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                            .clickable { activeCat = key }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                repeat(4 - rowCats.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (loading) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        } else {
            OutlinedTextField(
                value = syllabus,
                onValueChange = { syllabus = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("Full syllabus — units, topics, exam pattern, etc.") }
            )
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(status, fontSize = 11.sp, color = Color(0xFF1F7A3D))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    saving = true
                    FirebaseDatabase.getInstance().getReference("premiumContent").child(activeCat).child("syllabus")
                        .setValue(syllabus)
                        .addOnSuccessListener { saving = false; status = "Saved ✓" }
                        .addOnFailureListener { saving = false; status = "Failed to save" }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(if (saving) "Saving..." else "Save Syllabus", fontWeight = FontWeight.Bold)
            }
        }
    }
}


package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

@Composable
fun AdminContentEditorCard() {
    var coachingLine1 by remember { mutableStateOf("") }
    var tagline by remember { mutableStateOf("") }
    var trustBadge by remember { mutableStateOf("") }
    var statStudents by remember { mutableStateOf("") }
    var statSuccess by remember { mutableStateOf("") }
    var statYears by remember { mutableStateOf("") }
    var supportNumber by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("content")
            .get()
            .addOnSuccessListener { s ->
                coachingLine1 = s.child("coachingLine1").getValue(String::class.java) ?: ""
                tagline = s.child("tagline").getValue(String::class.java) ?: ""
                trustBadge = s.child("trustBadge").getValue(String::class.java) ?: ""
                statStudents = s.child("statStudents").getValue(String::class.java) ?: ""
                statSuccess = s.child("statSuccess").getValue(String::class.java) ?: ""
                statYears = s.child("statYears").getValue(String::class.java) ?: ""
                supportNumber = s.child("supportNumber").getValue(String::class.java) ?: ""
                loaded = true
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Content Editor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(14.dp))

        if (!loaded) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        } else {
            AdminField("Coaching Name", coachingLine1) { coachingLine1 = it }
            AdminField("Tagline", tagline) { tagline = it }
            AdminField("Trust Badge", trustBadge) { trustBadge = it }
            AdminField("Stat: Students", statStudents) { statStudents = it }
            AdminField("Stat: Success Rate", statSuccess) { statSuccess = it }
            AdminField("Stat: Years", statYears) { statYears = it }
            AdminField("Support Mobile Number", supportNumber) { supportNumber = it }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    saving = true
                    status = ""
                    val updates = mapOf(
                        "coachingLine1" to coachingLine1,
                        "tagline" to tagline,
                        "trustBadge" to trustBadge,
                        "statStudents" to statStudents,
                        "statSuccess" to statSuccess,
                        "statYears" to statYears,
                        "supportNumber" to supportNumber
                    )
                    FirebaseDatabase.getInstance().getReference("content").updateChildren(updates)
                        .addOnSuccessListener { saving = false; status = "Saved ✓" }
                        .addOnFailureListener { saving = false; status = "Failed to save" }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text(if (saving) "Saving..." else "Save Changes", fontWeight = FontWeight.Bold)
            }
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(status, fontSize = 12.sp, color = Color(0xFF1F7A3D))
            }
        }
    }
}

@Composable
private fun AdminField(label: String, value: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )
    }
}

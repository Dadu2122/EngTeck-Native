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

// selfAssessment/{catKey} -> { raw }  (max 50 questions, same paste format as everywhere else)
private val SA_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)
private const val SA_MAX = 50

private fun countSaQuestions(raw: String): Int {
    if (raw.isBlank()) return 0
    return raw.trim().split(Regex("\n\\s*\n")).count { it.isNotBlank() }
}

@Composable
fun AdminSelfAssessmentCard() {
    var activeCat by remember { mutableStateOf("tgt") }
    var raw by remember(activeCat) { mutableStateOf("") }
    var loading by remember(activeCat) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(activeCat) {
        loading = true
        status = ""
        FirebaseDatabase.getInstance().getReference("selfAssessment").child(activeCat).child("raw")
            .get()
            .addOnSuccessListener { s ->
                raw = s.getValue(String::class.java) ?: ""
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
        Text("🎯 Self Assessment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(4.dp))
        Text("50 Q timed test — 30 sec/question, paid students only", fontSize = 11.sp, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(12.dp))

        SA_CATS.chunked(4).forEach { rowCats ->
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
            Text(
                "1. Question text\nA) option\nB) option\nC) option\nD) option\nCorrect Answer: B\n\n2. Next question...",
                fontSize = 10.sp,
                color = Color(0xFF9B968A),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text("${countSaQuestions(raw)} / $SA_MAX questions saved", fontSize = 10.5.sp, color = Color(0xFF5B5F6B))

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(status, fontSize = 11.sp, color = Color(0xFF1F7A3D))
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    saving = true
                    FirebaseDatabase.getInstance().getReference("selfAssessment").child(activeCat).child("raw").setValue(raw)
                        .addOnSuccessListener { saving = false; status = "Saved ✓" }
                        .addOnFailureListener { saving = false; status = "Failed to save" }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(if (saving) "Saving..." else "Save Questions", fontWeight = FontWeight.Bold)
            }
        }
    }
}

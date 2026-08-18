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
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// dailyPractice/{catKey} -> { date, theoryRaw, literatureRaw, mixedRaw }
private val DP_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)
data class DpPart(val key: String, val label: String, val max: Int)
private val DP_PARTS = listOf(
    DpPart("theoryRaw", "Theories, Devices & Figures", 50),
    DpPart("literatureRaw", "Literature", 50),
    DpPart("mixedRaw", "Mixed — All Topics", 125)
)

private fun countDpQuestions(raw: String): Int {
    if (raw.isBlank()) return 0
    return raw.trim().split(Regex("\n\\s*\n")).count { it.isNotBlank() }
}

@Composable
fun AdminDailyPracticeCard() {
    var activeCat by remember { mutableStateOf("tgt") }
    var loading by remember(activeCat) { mutableStateOf(true) }
    var lastDate by remember(activeCat) { mutableStateOf("") }
    var partCounts by remember(activeCat) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var refreshTick by remember { mutableStateOf(0) }
    var editPart by remember { mutableStateOf<DpPart?>(null) }

    LaunchedEffect(activeCat, refreshTick) {
        loading = true
        FirebaseDatabase.getInstance().getReference("dailyPractice").child(activeCat)
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                lastDate = snapshot.child("date").getValue(String::class.java) ?: ""
                val result = mutableMapOf<String, Int>()
                DP_PARTS.forEach { p ->
                    val raw = snapshot.child(p.key).getValue(String::class.java) ?: ""
                    result[p.key] = countDpQuestions(raw)
                }
                partCounts = result
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
        Text("🔥 Daily Practice", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(4.dp))
        Text("225 questions/day — 50 Theories + 50 Literature + 125 Mixed", fontSize = 11.sp, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(12.dp))

        DP_CATS.chunked(4).forEach { rowCats ->
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
                "Last set: ${if (lastDate.isEmpty()) "Not generated yet" else lastDate}",
                fontSize = 11.5.sp,
                color = Color(0xFF5B5F6B)
            )
            Spacer(Modifier.height(10.dp))
            DP_PARTS.forEach { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                        Text("${partCounts[p.key] ?: 0} / ${p.max} questions", fontSize = 10.5.sp, color = Color(0xFF5B5F6B))
                    }
                    TextButton(onClick = { editPart = p }) { Text("✏️ Edit") }
                }
            }
        }
    }

    editPart?.let { p ->
        DailyPracticePartEditorDialog(
            catKey = activeCat,
            part = p,
            onDismiss = { editPart = null; refreshTick++ }
        )
    }
}

@Composable
private fun DailyPracticePartEditorDialog(catKey: String, part: DpPart, onDismiss: () -> Unit) {
    var raw by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("dailyPractice").child(catKey).child(part.key)
            .get()
            .addOnSuccessListener { s ->
                raw = s.getValue(String::class.java) ?: ""
                loaded = true
            }
            .addOnFailureListener { loaded = true }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(part.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(4.dp))
            Text("Max ${part.max} questions", fontSize = 11.sp, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(10.dp))

            if (!loaded) {
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
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 260.dp),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("${countDpQuestions(raw)} / ${part.max} questions detected", fontSize = 10.5.sp, color = Color(0xFF5B5F6B))

                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status, fontSize = 11.sp, color = Color(0xFF1F7A3D))
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        saving = true
                        val today = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date())
                        val ref = FirebaseDatabase.getInstance().getReference("dailyPractice").child(catKey)
                        ref.updateChildren(mapOf(part.key to raw, "date" to today))
                            .addOnSuccessListener { saving = false; status = "Saved ✓" }
                            .addOnFailureListener { saving = false; status = "Failed to save" }
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(if (saving) "Saving..." else "Save", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

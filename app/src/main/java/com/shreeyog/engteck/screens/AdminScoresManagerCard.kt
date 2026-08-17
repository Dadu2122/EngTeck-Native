package com.shreeyog.engteck.screens
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import java.util.*

data class ScoreRow(val mobile: String, val name: String, val score: Int, val total: Int, val pct: Int)
data class ManualStudentOption(val mobile: String, val label: String)

private val SCORE_CATS = listOf("tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC")

@Composable
fun AdminScoresManagerCard() {
    var activeCat by remember { mutableStateOf("tgt") }
    var scores by remember(activeCat) { mutableStateOf<List<ScoreRow>>(emptyList()) }
    var loading by remember(activeCat) { mutableStateOf(true) }
    var showAddForm by remember { mutableStateOf(false) }
    var editingMobile by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(activeCat, refreshTrigger) {
        loading = true
        FirebaseDatabase.getInstance().getReference("saScores").child(activeCat)
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                scores = snapshot.children.mapNotNull { s ->
                    val mobile = s.key ?: return@mapNotNull null
                    val name = s.child("name").getValue(String::class.java) ?: "Student"
                    val score = s.child("score").getValue(Long::class.java)?.toInt() ?: 0
                    val total = s.child("total").getValue(Long::class.java)?.toInt() ?: 1
                    val pct = s.child("pct").getValue(Long::class.java)?.toInt() ?: ((score * 100) / total)
                    ScoreRow(mobile, name, score, total, pct)
                }
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
        Text("Manage Self Assessment Scores", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SCORE_CATS.forEach { (key, label) ->
                val active = activeCat == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC))
                        .clickable { activeCat = key; showAddForm = false; editingMobile = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = { showAddForm = !showAddForm },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B79)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text(if (showAddForm) "Cancel" else "＋ Add Score Manually", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        if (showAddForm) {
            Spacer(Modifier.height(12.dp))
            AddScoreForm(
                catKey = activeCat,
                onDone = {
                    showAddForm = false
                    refreshTrigger++
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        } else if (scores.isEmpty()) {
            Text("No scores in this category yet.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
        } else {
            Column(modifier = Modifier.heightIn(max = 360.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scores) { row ->
                        if (editingMobile == row.mobile) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = editingName,
                                    onValueChange = { editingName = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Save",
                                    color = Color(0xFF1F7A3D),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        if (editingName.isNotBlank()) {
                                            FirebaseDatabase.getInstance().getReference("saScores")
                                                .child(activeCat).child(row.mobile)
                                                .child("name").setValue(editingName.trim())
                                            editingMobile = null
                                            refreshTrigger++
                                        }
                                    }
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${row.name} — ${row.score}/${row.total} (${row.pct}%)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1A1A1A),
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF3B6EA8), RoundedCornerShape(6.dp))
                                            .clickable {
                                                editingMobile = row.mobile
                                                editingName = row.name
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text("Edit", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFE85D4C), RoundedCornerShape(6.dp))
                                            .clickable {
                                                FirebaseDatabase.getInstance().getReference("saScores")
                                                    .child(activeCat).child(row.mobile).removeValue()
                                                refreshTrigger++
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text("✕", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddScoreForm(catKey: String, onDone: () -> Unit) {
    var options by remember(catKey) { mutableStateOf<List<ManualStudentOption>>(emptyList()) }
    var selectedMobile by remember(catKey) { mutableStateOf("") }
    var nameField by remember { mutableStateOf("") }
    var scoreField by remember { mutableStateOf("") }
    var totalField by remember { mutableStateOf("") }
    var dropdownOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(catKey) {
        FirebaseDatabase.getInstance().getReference("registrations")
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.children.mapNotNull { r ->
                    val addedByAdmin = r.child("addedByAdmin").getValue(Boolean::class.java) ?: false
                    val cat = r.child("planCategory").getValue(String::class.java) ?: return@mapNotNull null
                    val mobile = r.child("mobile").getValue(String::class.java) ?: return@mapNotNull null
                    if (!addedByAdmin || !cat.equals(catKey, ignoreCase = true)) return@mapNotNull null
                    val nickname = r.child("nickname").getValue(String::class.java)?.trim()
                    val name = r.child("name").getValue(String::class.java) ?: "Student"
                    val displayName = if (!nickname.isNullOrEmpty()) nickname else name
                    ManualStudentOption(mobile, "$displayName — $mobile")
                }
                options = list
                if (list.isNotEmpty()) {
                    selectedMobile = list[0].mobile
                    nameField = list[0].label.substringBefore(" — ")
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F3EC), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text("Add Score Manually — ${catKey.uppercase()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(10.dp))

        if (options.isEmpty()) {
            Text("No manually-added students found in this category.", fontSize = 11.5.sp, color = Color(0xFF5B5F6B))
        } else {
            Text("Student (Manually Added Only)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            Box {
                OutlinedButton(onClick = { dropdownOpen = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        options.firstOrNull { it.mobile == selectedMobile }?.label ?: "Choose",
                        color = Color(0xFF1A1A1A),
                        fontSize = 12.sp
                    )
                }
                DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                    options.forEach { opt ->
                        DropdownMenuItem(text = { Text(opt.label, fontSize = 12.sp) }, onClick = {
                            selectedMobile = opt.mobile
                            nameField = opt.label.substringBefore(" — ")
                            dropdownOpen = false
                        })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            AdminSimpleField("Name to show (Nickname)", nameField) { nameField = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    AdminSimpleField("Score", scoreField, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) { scoreField = it }
                }
                Box(Modifier.weight(1f)) {
                    AdminSimpleField("Total Marks", totalField, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) { totalField = it }
                }
            }

            Button(
                onClick = {
                    val score = scoreField.toIntOrNull()
                    val total = totalField.toIntOrNull()
                    if (selectedMobile.isBlank() || nameField.isBlank() || score == null || total == null || total <= 0 || score < 0 || score > total) {
                        status = "Please fill correct Score and Total (Score cannot exceed Total)"
                        return@Button
                    }
                    saving = true
                    status = ""
                    val pct = (score * 100) / total
                    val entry = mapOf(
                        "name" to nameField,
                        "score" to score,
                        "total" to total,
                        "pct" to pct,
                        "dateISO" to java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                        "ts" to System.currentTimeMillis()
                    )
                    FirebaseDatabase.getInstance().getReference("saScores")
                        .child(catKey).child(selectedMobile).setValue(entry)
                        .addOnSuccessListener {
                            saving = false
                            onDone()
                        }
                        .addOnFailureListener {
                            saving = false
                            status = "Failed to save score"
                        }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text(if (saving) "Saving..." else "Save Score", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(status, fontSize = 11.sp, color = Color(0xFFC0392B))
            }
        }
    }
}

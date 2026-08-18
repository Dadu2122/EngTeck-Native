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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.FirebaseDatabase

private val PPDF_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)

data class PremiumSetEntry(val key: String, val title: String, val questionCount: Int)

private fun countPremiumQuestions(raw: String): Int {
    if (raw.isBlank()) return 0
    return raw.trim().split(Regex("\n\\s*\n")).count { it.isNotBlank() }
}

@Composable
fun AdminPremiumPdfSetsCard() {
    var activeCat by remember { mutableStateOf("tgt") }
    var loading by remember(activeCat) { mutableStateOf(true) }
    var sets by remember(activeCat) { mutableStateOf<List<PremiumSetEntry>>(emptyList()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorCat by remember { mutableStateOf("tgt") }
    var editorSetKey by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var markPaidOpen by remember { mutableStateOf(false) }

    LaunchedEffect(activeCat, refreshTick) {
        loading = true
        FirebaseDatabase.getInstance().getReference("paidPdfLibrary").child(activeCat).child("sets")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                sets = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val title = child.child("title").getValue(String::class.java) ?: key
                    val raw = child.child("questionsRaw").getValue(String::class.java) ?: ""
                    PremiumSetEntry(key, title, countPremiumQuestions(raw))
                }.sortedBy { it.key }
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
        Text("Premium PDF Library", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(4.dp))
        Text(
            "Paid-plan sets — TGT / PGT / LT / GIC / UPESSC / UPHESC / NET",
            fontSize = 11.sp,
            color = Color(0xFF5B5F6B)
        )
        Spacer(Modifier.height(12.dp))

        PPDF_CATS.chunked(4).forEach { rowCats ->
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
        Spacer(Modifier.height(8.dp))

        if (loading) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        } else {
            if (sets.isEmpty()) {
                Text("No premium sets yet in this category.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(10.dp))
            } else {
                Column(modifier = Modifier.heightIn(max = 320.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sets) { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                                    Text("${s.questionCount} questions", fontSize = 10.5.sp, color = Color(0xFF5B5F6B))
                                }
                                TextButton(onClick = {
                                    editorCat = activeCat
                                    editorSetKey = s.key
                                    editorOpen = true
                                }) { Text("Edit") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Button(
                onClick = {
                    editorCat = activeCat
                    editorSetKey = null
                    editorOpen = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("+ Add New Premium Set", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { markPaidOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B79), contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("🔓 Mark PDF Access as Paid", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (editorOpen) {
        PremiumSetEditorDialog(
            catKey = editorCat,
            setKey = editorSetKey,
            onDismiss = { editorOpen = false },
            onSaved = {
                editorOpen = false
                refreshTick++
            }
        )
    }
    if (markPaidOpen) {
        MarkPaidDialog(
            catKey = activeCat,
            catLabel = PPDF_CATS.find { it.first == activeCat }?.second ?: activeCat,
            firebasePath = "paidPdfCategories",
            actionLabel = "read/download all",
            contentLabel = "premium PDF sets",
            onDismiss = { markPaidOpen = false }
        )
    }
}

@Composable
private fun MarkPaidDialog(
    catKey: String,
    catLabel: String,
    firebasePath: String,
    actionLabel: String,
    contentLabel: String,
    onDismiss: () -> Unit
) {
    var mobile by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text("Mark $catLabel Access as Paid", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(12.dp))
            Text("Student Mobile Number", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                placeholder = { Text("10-digit mobile number") }
            )
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(status, fontSize = 11.5.sp, color = if (status.startsWith("Marked")) Color(0xFF1F7A3D) else Color(0xFFC0392B))
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (mobile.length != 10) { status = "Please enter a valid 10-digit mobile number."; return@Button }
                    saving = true
                    FirebaseDatabase.getInstance().getReference(firebasePath).child(mobile).child(catKey).setValue(true)
                        .addOnSuccessListener {
                            saving = false
                            status = "Marked as paid ✓ — $mobile can now $actionLabel $catLabel $contentLabel."
                        }
                        .addOnFailureListener { saving = false; status = "Failed to save." }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(if (saving) "Saving..." else "Mark as Paid", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

@Composable
private fun PremiumSetEditorDialog(catKey: String, setKey: String?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(setKey == null) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(setKey) {
        if (setKey != null) {
            FirebaseDatabase.getInstance().getReference("paidPdfLibrary").child(catKey).child("sets").child(setKey)
                .get()
                .addOnSuccessListener { s ->
                    title = s.child("title").getValue(String::class.java) ?: ""
                    raw = s.child("questionsRaw").getValue(String::class.java) ?: ""
                    loaded = true
                }
                .addOnFailureListener { loaded = true }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(
                if (setKey == null) "Add New Premium Set" else "Edit Premium Set",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF12203D)
            )
            Spacer(Modifier.height(12.dp))

            if (!loaded) {
                CircularProgressIndicator(color = Color(0xFF12203D))
            } else {
                Text("Set Title", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    placeholder = { Text("e.g. TGT — Premium Set 1") }
                )
                Spacer(Modifier.height(12.dp))

                Text("Questions (paste format)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(4.dp))
                Text(
                    "1. Question text\nA) option\nB) option\nC) option\nD) option\nCorrect Answer: B\n\n2. Next question...",
                    fontSize = 10.sp,
                    color = Color(0xFF9B968A),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 220.dp),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("${countPremiumQuestions(raw)} questions detected", fontSize = 10.5.sp, color = Color(0xFF5B5F6B))

                if (errorMsg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(errorMsg, fontSize = 11.sp, color = Color(0xFFC0392B))
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                errorMsg = "Title is required."
                                return@Button
                            }
                            saving = true
                            errorMsg = ""
                            val ref = FirebaseDatabase.getInstance().getReference("paidPdfLibrary").child(catKey).child("sets")
                            val targetKey = setKey ?: ref.push().key
                            if (targetKey == null) {
                                saving = false
                                errorMsg = "Could not generate set key."
                                return@Button
                            }
                            val updates = mapOf("title" to title, "questionsRaw" to raw)
                            ref.child(targetKey).updateChildren(updates)
                                .addOnSuccessListener { saving = false; onSaved() }
                                .addOnFailureListener { saving = false; errorMsg = "Failed to save." }
                        },
                        enabled = !saving && !deleting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text(if (saving) "Saving..." else "Save", fontWeight = FontWeight.Bold)
                    }
                    if (setKey != null) {
                        Button(
                            onClick = {
                                deleting = true
                                FirebaseDatabase.getInstance().getReference("paidPdfLibrary").child(catKey).child("sets").child(setKey)
                                    .removeValue()
                                    .addOnSuccessListener { deleting = false; onSaved() }
                                    .addOnFailureListener { deleting = false; errorMsg = "Failed to delete." }
                            },
                            enabled = !saving && !deleting,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D4C), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(if (deleting) "..." else "Delete", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

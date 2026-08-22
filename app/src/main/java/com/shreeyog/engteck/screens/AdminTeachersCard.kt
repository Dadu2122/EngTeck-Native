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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

data class TeacherEntry(val key: String, val name: String, val pin: String)

@Composable
fun AdminTeachersCard() {
    var teachers by remember { mutableStateOf<List<TeacherEntry>>(emptyList()) }
    var liveStatus by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingTeacher by remember { mutableStateOf<TeacherEntry?>(null) }

    DisposableEffect(Unit) {
        val ref = FirebaseDatabase.getInstance().getReference("teachers")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                loading = false
                teachers = snapshot.children.mapNotNull { c ->
                    val name = c.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    val pin = c.child("adminPin").getValue(String::class.java) ?: ""
                    TeacherEntry(c.key ?: "", name, pin)
                }
            }
            override fun onCancelled(error: DatabaseError) { loading = false }
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    // Live status per teacher — each gets its own tiny listener on liveClasses/{key}/active.
    teachers.forEach { t ->
        DisposableEffect(t.key) {
            val ref = FirebaseDatabase.getInstance().getReference("liveClasses/${t.key}/active")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isLive = snapshot.getValue(Boolean::class.java) ?: false
                    liveStatus = liveStatus + (t.key to isLive)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            ref.addValueEventListener(listener)
            onDispose { ref.removeEventListener(listener) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(
            "Har teacher ka apna naam + PIN — multiple teachers ek saath, alag-alag apni live class chala sakte hain (har ek ki apni class, apna chat/doubts/MCQ — sab alag).",
            fontSize = 11.5.sp, color = Color(0xFF5B5F6B), lineHeight = 16.sp
        )
        Spacer(Modifier.height(14.dp))

        if (loading) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        } else if (teachers.isEmpty()) {
            Text("Koi teacher add nahi hai abhi.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                teachers.forEach { t ->
                    val isLive = liveStatus[t.key] ?: false
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF5F3EC), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${t.name} — PIN: ${t.pin}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                            if (isLive) {
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(7.dp).background(Color(0xFF1F7A3D), CircleShape))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Live", fontSize = 11.sp, color = Color(0xFF1F7A3D), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFD4A017))
                                .clickable { deletingTeacher = t }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("✕", color = Color(0xFF12203D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { showAddDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D4C)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("+ Add Teacher", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

    if (showAddDialog) {
        AddTeacherDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, pin ->
                val ref = FirebaseDatabase.getInstance().getReference("teachers").push()
                ref.setValue(mapOf("name" to name, "adminPin" to pin))
                showAddDialog = false
            }
        )
    }

    deletingTeacher?.let { t ->
        AlertDialog(
            onDismissRequest = { deletingTeacher = null },
            title = { Text("Remove ${t.name}?", fontWeight = FontWeight.Bold) },
            text = { Text("Ye teacher ka login PIN hamesha ke liye hat jayega. Unki live class bhi band ho jayegi.") },
            confirmButton = {
                TextButton(onClick = {
                    val db = FirebaseDatabase.getInstance()
                    db.getReference("teachers").child(t.key).removeValue()
                    db.getReference("liveClasses").child(t.key).removeValue()
                    deletingTeacher = null
                }) { Text("Remove", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTeacher = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddTeacherDialog(onDismiss: () -> Unit, onSave: (name: String, pin: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf((1000..9999).random().toString()) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Teacher", fontWeight = FontWeight.Bold, color = Color(0xFF12203D)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Teacher Name *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 6) pin = it.filter { c -> c.isDigit() } },
                    label = { Text("Admin PIN") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Auto-generated — ise change kar sakte ho, teacher isi PIN se admin panel me login karega.", fontSize = 10.5.sp, color = Color(0xFF8A8F99))
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = Color(0xFFC0392B), fontSize = 11.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || pin.isBlank()) {
                    error = "Naam aur PIN dono zaroori hain"
                    return@TextButton
                }
                onSave(name.trim(), pin.trim())
            }) { Text("Add", color = Color(0xFF1F7A3D), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

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
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.FirebaseDatabase

// videoLibrary/{catKey}/videos/{key} -> { title, url }
// classRecordings/{catKey}/recordings/{key} -> { title, date, url }
private val VR_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)

data class VideoEntry(val key: String, val title: String, val url: String)
data class RecordingEntry(val key: String, val title: String, val date: String, val url: String)

@Composable
fun AdminVideosRecordingsCard() {
    var activeTab by remember { mutableStateOf("videos") }
    var activeCat by remember { mutableStateOf("tgt") }
    var refreshTick by remember { mutableStateOf(0) }

    var videos by remember(activeCat, activeTab, refreshTick) { mutableStateOf<List<VideoEntry>>(emptyList()) }
    var recordings by remember(activeCat, activeTab, refreshTick) { mutableStateOf<List<RecordingEntry>>(emptyList()) }
    var loading by remember(activeCat, activeTab) { mutableStateOf(true) }

    var addVideoOpen by remember { mutableStateOf(false) }
    var addRecordingOpen by remember { mutableStateOf(false) }
    var markPaidOpen by remember { mutableStateOf(false) }

    LaunchedEffect(activeCat, activeTab, refreshTick) {
        loading = true
        if (activeTab == "videos") {
            FirebaseDatabase.getInstance().getReference("videoLibrary").child(activeCat).child("videos")
                .get()
                .addOnSuccessListener { snapshot ->
                    loading = false
                    videos = snapshot.children.mapNotNull { child ->
                        val key = child.key ?: return@mapNotNull null
                        VideoEntry(
                            key,
                            child.child("title").getValue(String::class.java) ?: "Video",
                            child.child("url").getValue(String::class.java) ?: ""
                        )
                    }
                }
                .addOnFailureListener { loading = false }
        } else {
            FirebaseDatabase.getInstance().getReference("classRecordings").child(activeCat).child("recordings")
                .get()
                .addOnSuccessListener { snapshot ->
                    loading = false
                    recordings = snapshot.children.mapNotNull { child ->
                        val key = child.key ?: return@mapNotNull null
                        RecordingEntry(
                            key,
                            child.child("title").getValue(String::class.java) ?: "Recording",
                            child.child("date").getValue(String::class.java) ?: "",
                            child.child("url").getValue(String::class.java) ?: ""
                        )
                    }
                }
                .addOnFailureListener { loading = false }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Videos & Class Recordings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("videos" to "🎬 Videos", "recordings" to "📼 Recordings").forEach { (key, label) ->
                val active = activeTab == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC), RoundedCornerShape(100.dp))
                        .clickable { activeTab = key }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        VR_CATS.chunked(4).forEach { rowCats ->
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
        } else if (activeTab == "videos") {
            if (videos.isEmpty()) {
                Text("No videos added yet.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(10.dp))
            } else {
                Column(modifier = Modifier.heightIn(max = 300.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(videos) { v ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("▸ ${v.title}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    FirebaseDatabase.getInstance().getReference("videoLibrary").child(activeCat)
                                        .child("videos").child(v.key).removeValue()
                                        .addOnSuccessListener { refreshTick++ }
                                }) { Text("✕", color = Color(0xFFC0392B)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = { addVideoOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("+ Add Video", fontWeight = FontWeight.Bold)
            }
        } else {
            if (recordings.isEmpty()) {
                Text("No recordings added yet.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(10.dp))
            } else {
                Column(modifier = Modifier.heightIn(max = 300.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recordings) { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("▸ ${r.title}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                                    if (r.date.isNotEmpty()) Text(r.date, fontSize = 10.sp, color = Color(0xFF5B5F6B))
                                }
                                TextButton(onClick = {
                                    FirebaseDatabase.getInstance().getReference("classRecordings").child(activeCat)
                                        .child("recordings").child(r.key).removeValue()
                                        .addOnSuccessListener { refreshTick++ }
                                }) { Text("✕", color = Color(0xFFC0392B)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = { addRecordingOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("+ Add Recording", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { markPaidOpen = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B79), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text("🔓 Mark Video/Recording Access as Paid", fontWeight = FontWeight.Bold)
        }
    }

    if (addVideoOpen) {
        AddVideoDialog(catKey = activeCat, onDismiss = { addVideoOpen = false }, onSaved = { addVideoOpen = false; refreshTick++ })
    }
    if (addRecordingOpen) {
        AddRecordingDialog(catKey = activeCat, onDismiss = { addRecordingOpen = false }, onSaved = { addRecordingOpen = false; refreshTick++ })
    }
    if (markPaidOpen) {
        MarkVideoPaidDialog(
            catKey = activeCat,
            catLabel = VR_CATS.find { it.first == activeCat }?.second ?: activeCat,
            onDismiss = { markPaidOpen = false }
        )
    }
}

@Composable
private fun MarkVideoPaidDialog(catKey: String, catLabel: String, onDismiss: () -> Unit) {
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
            Text("Mark $catLabel Video Access as Paid", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
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
                    FirebaseDatabase.getInstance().getReference("paidVideoCategories").child(mobile).child(catKey).setValue(true)
                        .addOnSuccessListener {
                            saving = false
                            status = "Marked as paid ✓ — $mobile can now watch all $catLabel videos & recordings."
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
private fun AddVideoDialog(catKey: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text("Add Video", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(12.dp))
            Text("Video Title", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                placeholder = { Text("e.g. Tenses Explained") }
            )
            Spacer(Modifier.height(12.dp))
            Text("YouTube Link", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                placeholder = { Text("https://youtu.be/...") }
            )
            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(errorMsg, fontSize = 11.sp, color = Color(0xFFC0392B))
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (url.isBlank()) { errorMsg = "Video link is required."; return@Button }
                    saving = true
                    FirebaseDatabase.getInstance().getReference("videoLibrary").child(catKey).child("videos")
                        .push().setValue(mapOf("title" to (title.ifBlank { "Video" }), "url" to url))
                        .addOnSuccessListener { saving = false; onSaved() }
                        .addOnFailureListener { saving = false; errorMsg = "Failed to save." }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(if (saving) "Saving..." else "Add", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
private fun AddRecordingDialog(catKey: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text("Add Recording", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(12.dp))
            Text("Recording Title", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                placeholder = { Text("e.g. Hamlet — Act 3 discussion") }
            )
            Spacer(Modifier.height(12.dp))
            Text("Class Date", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = date, onValueChange = { date = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                placeholder = { Text("YYYY-MM-DD e.g. 2026-08-18") }
            )
            Spacer(Modifier.height(12.dp))
            Text("Google Drive / YouTube Link", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                placeholder = { Text("https://drive.google.com/...") }
            )
            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(errorMsg, fontSize = 11.sp, color = Color(0xFFC0392B))
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (url.isBlank()) { errorMsg = "Link is required."; return@Button }
                    saving = true
                    FirebaseDatabase.getInstance().getReference("classRecordings").child(catKey).child("recordings")
                        .push().setValue(mapOf("title" to (title.ifBlank { "Recording" }), "date" to date, "url" to url))
                        .addOnSuccessListener { saving = false; onSaved() }
                        .addOnFailureListener { saving = false; errorMsg = "Failed to save." }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(if (saving) "Saving..." else "Add", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

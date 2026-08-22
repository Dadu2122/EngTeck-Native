package com.shreeyog.engteck.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream

private fun decodeBase64PhotoAdmin(raw: String?): Bitmap? {
    if (raw.isNullOrBlank()) return null
    return try {
        val pureBase64 = if (raw.contains(",")) raw.substringAfter(",") else raw
        val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun AdminTeacherProfileCard() {
    val context = LocalContext.current
    var teacherName by remember { mutableStateOf("") }
    var roleLabel by remember { mutableStateOf("FACULTY") }
    var qual1 by remember { mutableStateOf("") }
    var qual2 by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("content")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                teacherName = snapshot.child("teacherName").getValue(String::class.java) ?: ""
                roleLabel = snapshot.child("roleLabel").getValue(String::class.java) ?: "FACULTY"
                qual1 = snapshot.child("qual1").getValue(String::class.java) ?: ""
                qual2 = snapshot.child("qual2").getValue(String::class.java) ?: ""
                photoBase64 = snapshot.child("teacherPhotoBase64").getValue(String::class.java)
            }
            .addOnFailureListener { loading = false }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadingPhoto = true
            try {
                val input = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(input)
                input?.close()
                if (original != null) {
                    // Downscale so base64 stays small enough for Realtime Database (no Storage/Blaze needed).
                    val maxDim = 500
                    val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height))
                    val resized = if (scale < 1f) {
                        Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
                    } else original
                    val out = ByteArrayOutputStream()
                    resized.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    val b64 = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    photoBase64 = b64
                    statusMsg = "Photo ready — ab neeche 'Save' dabao."
                } else {
                    statusMsg = "Photo padhne me dikkat aayi."
                }
            } catch (e: Exception) {
                statusMsg = "Upload failed: ${e.message}"
            }
            uploadingPhoto = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Teacher Profile Card", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(14.dp))

        if (loading) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        } else {
            val previewBitmap = remember(photoBase64) { decodeBase64PhotoAdmin(photoBase64) }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(110.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF5F3EC))
                    .border(2.dp, Color(0xFFD4A017), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Teacher photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Text("No Photo", fontSize = 11.sp, color = Color(0xFF8A8F99))
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { photoPickerLauncher.launch("image/*") },
                enabled = !uploadingPhoto,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B6EA8)),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally).height(44.dp)
            ) {
                if (uploadingPhoto) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("📁 Change Teacher Photo", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Teacher Name", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = teacherName, onValueChange = { teacherName = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                placeholder = { Text("e.g. Amar Sharma") }
            )
            Spacer(Modifier.height(12.dp))
            Text("Role Label", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = roleLabel, onValueChange = { roleLabel = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                placeholder = { Text("e.g. FACULTY / PRINCIPAL") }
            )
            Spacer(Modifier.height(12.dp))
            Text("Qualification", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = qual1, onValueChange = { qual1 = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                placeholder = { Text("e.g. M.A. English, B.Ed.") }
            )
            Spacer(Modifier.height(12.dp))
            Text("Exams Qualified", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = qual2, onValueChange = { qual2 = it },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
                placeholder = { Text("e.g. UGC-NET, UPTET") }
            )

            if (statusMsg.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(statusMsg, fontSize = 11.5.sp, color = Color(0xFF946B00))
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    saving = true
                    val data = mutableMapOf<String, Any>(
                        "teacherName" to teacherName,
                        "roleLabel" to roleLabel,
                        "qual1" to qual1,
                        "qual2" to qual2
                    )
                    photoBase64?.let { data["teacherPhotoBase64"] = it }
                    FirebaseDatabase.getInstance().getReference("content")
                        .updateChildren(data)
                        .addOnSuccessListener { saving = false; statusMsg = "Saved ✓" }
                        .addOnFailureListener { saving = false; statusMsg = "Save failed — try again." }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text(if (saving) "Saving..." else "💾 Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

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
fun AdminMiniBookUploadCard() {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Upload Mini Book", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(4.dp))
        Text(
            "Paste text using # for a heading and blank lines for new paragraphs.",
            fontSize = 11.sp,
            color = Color(0xFF5B5F6B)
        )
        Spacer(Modifier.height(14.dp))

        Text("Book Title", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        Text("Content", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                if (title.isBlank() || content.isBlank()) {
                    status = "Please fill title and content"
                    return@Button
                }
                saving = true
                status = ""
                val db = FirebaseDatabase.getInstance()
                val newRef = db.getReference("miniBooks").push()
                val key = newRef.key ?: return@Button
                val bookData = mapOf(
                    "title" to title,
                    "addedAt" to System.currentTimeMillis(),
                    "downloads" to 0
                )
                newRef.setValue(bookData)
                    .addOnSuccessListener {
                        db.getReference("miniBooksContent").child(key)
                            .setValue(mapOf("pastedText" to content))
                            .addOnSuccessListener {
                                saving = false
                                status = "Book uploaded ✓"
                                title = ""
                                content = ""
                            }
                            .addOnFailureListener {
                                saving = false
                                status = "Failed to save content"
                            }
                    }
                    .addOnFailureListener {
                        saving = false
                        status = "Failed to upload"
                    }
            },
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(if (saving) "Uploading..." else "Upload Book", fontWeight = FontWeight.Bold)
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(status, fontSize = 12.sp, color = Color(0xFF1F7A3D))
        }
    }
}

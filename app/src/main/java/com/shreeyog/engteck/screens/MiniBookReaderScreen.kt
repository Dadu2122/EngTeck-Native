package com.shreeyog.engteck.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

@Composable
fun MiniBookReaderScreen(bookKey: String, title: String, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var content by remember { mutableStateOf("") }

    LaunchedEffect(bookKey) {
        FirebaseDatabase.getInstance().getReference("miniBooksContent").child(bookKey).child("pastedText")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                content = snapshot.getValue(String::class.java) ?: "No content found."
            }
            .addOnFailureListener {
                loading = false
                content = "Could not load content."
            }
    }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(16.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF12203D))
                }
            } else {
                content.split("\n\n").forEach { block ->
                    val trimmed = block.trim()
                    if (trimmed.startsWith("#")) {
                        Text(
                            trimmed.removePrefix("#").trim(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7A2E3D),
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                        )
                    } else if (trimmed.isNotEmpty()) {
                        Text(
                            trimmed,
                            fontSize = 14.sp,
                            color = Color(0xFF1A1A1A),
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

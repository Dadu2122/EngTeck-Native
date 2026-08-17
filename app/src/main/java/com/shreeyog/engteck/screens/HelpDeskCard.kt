package com.shreeyog.engteck.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

private val FAQS = listOf(
    "PDF not opening?" to "Check your internet connection and tap \"View\" again. If it still doesn't open, contact us on WhatsApp.",
    "Video not loading?" to "Try on Wifi or a strong mobile data connection. Videos load from YouTube, so weak networks may take longer.",
    "Content didn't unlock after payment?" to "After payment, get your access code from the teacher on WhatsApp, then enter that mobile number and code in the app."
)

@Composable
fun HelpDeskCard() {
    var supportNumber by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var feedbackStatus by remember { mutableStateOf("") }
    val context = LocalContext.current
    var expandedFaq by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("content").child("supportNumber")
            .get()
            .addOnSuccessListener { supportNumber = it.getValue(String::class.java) ?: "" }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text(
            "SUPPORT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Color(0xFF1B6B79)
        )
        Spacer(Modifier.height(6.dp))
        Text("Help Desk", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF25D366), RoundedCornerShape(12.dp))
                    .clickable {
                        if (supportNumber.isNotEmpty()) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$supportNumber")))
                            } catch (e: Exception) { }
                        }
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF1B6B79), RoundedCornerShape(12.dp))
                    .clickable {
                        if (supportNumber.isNotEmpty()) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91$supportNumber")))
                            } catch (e: Exception) { }
                        }
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Call Us", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        FAQS.forEachIndexed { index, (q, a) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedFaq = if (expandedFaq == index) -1 else index }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(q, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                    Text(if (expandedFaq == index) "▲" else "▼", color = Color(0xFF5B5F6B), fontSize = 12.sp)
                }
                if (expandedFaq == index) {
                    Text(
                        a,
                        fontSize = 12.sp,
                        color = Color(0xFF5B5F6B),
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Hello, dear Student, how are you?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = feedback,
            onValueChange = { if (it.length <= 500) feedback = it },
            placeholder = { Text("Type your feedback here…") },
            modifier = Modifier.fillMaxWidth().height(90.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (feedback.isBlank()) {
                    feedbackStatus = "Please write something first"
                    return@Button
                }
                FirebaseDatabase.getInstance().getReference("helpFeedback").push()
                    .setValue(mapOf("text" to feedback, "timestamp" to System.currentTimeMillis()))
                    .addOnSuccessListener {
                        feedbackStatus = "Feedback sent ✓"
                        feedback = ""
                    }
                    .addOnFailureListener {
                        feedbackStatus = "Something went wrong, please try again"
                    }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D4C)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Submit", fontWeight = FontWeight.Bold, color = Color.White)
        }
        if (feedbackStatus.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(feedbackStatus, fontSize = 12.sp, color = Color(0xFF1F7A3D))
        }
    }
}

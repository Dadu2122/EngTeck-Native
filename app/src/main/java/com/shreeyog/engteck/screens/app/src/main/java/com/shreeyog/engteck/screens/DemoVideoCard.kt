package com.shreeyog.engteck.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

@Composable
fun DemoVideoCard() {
    var videoUrl by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("content").child("demoVideoUrl")
            .get()
            .addOnSuccessListener { snapshot ->
                videoUrl = snapshot.getValue(String::class.java) ?: ""
            }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        Text(
            "WATCH NOW",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Color(0xFF1B6B79)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Coaching and App Updates",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(14.dp))
                .clickable {
                    if (videoUrl.isNotBlank()) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)))
                        } catch (e: Exception) { }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF0000).copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 20.sp)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12203D))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFF0000)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 8.sp)
                }
                Text("Class Demo — Watch Free", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

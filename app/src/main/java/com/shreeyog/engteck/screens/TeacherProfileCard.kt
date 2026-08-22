package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase

data class ProfileContent(
    val teacherName: String = "Teacher Name",
    val roleLabel: String = "FACULTY",
    val qual1: String = "M.A. English, B.Ed.",
    val qual2: String = "UGC-NET, UPTET",
    val teacherPhotoBase64: String? = null
)

@Composable
fun TeacherProfileCard() {
    var content by remember { mutableStateOf(ProfileContent()) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("content")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    content = ProfileContent(
                        teacherName = snapshot.child("teacherName").getValue(String::class.java) ?: content.teacherName,
                        roleLabel = snapshot.child("roleLabel").getValue(String::class.java) ?: content.roleLabel,
                        qual1 = snapshot.child("qual1").getValue(String::class.java) ?: content.qual1,
                        qual2 = snapshot.child("qual2").getValue(String::class.java) ?: content.qual2,
                        teacherPhotoBase64 = snapshot.child("teacherPhotoBase64").getValue(String::class.java)
                    )
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .offset(y = (-28).dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFDFE6EF), Color(0xFFC7D0DD))))
                    .border(3.dp, Color(0xFFD4A017), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!content.teacherPhotoBase64.isNullOrBlank()) {
                    AsyncImage(
                        model = content.teacherPhotoBase64,
                        contentDescription = content.teacherName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp))
                    )
                } else {
                    Text("Photo", fontSize = 11.sp, color = Color(0xFF5B5F6B), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Name + role bar — matches the website's navy "AMAR SH • FACULTY" pill.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF12203D))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(content.teacherName, color = Color(0xFFD4A017), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF1F9D55)))
                Spacer(Modifier.width(6.dp))
                Text(content.roleLabel, color = Color(0xFF1F9D55), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QualCard("QUALIFICATION", content.qual1, Modifier.weight(1f))
                QualCard("EXAMS QUALIFIED", content.qual2, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(Color(0xFFEAF6E9), RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF1F7A3D)))
                Text("Currently Taking Live Batches", color = Color(0xFF1F7A3D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QualCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFFF5F3EC), RoundedCornerShape(14.dp))
            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 10.sp, color = Color(0xFF5B5F6B), fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
    }
}

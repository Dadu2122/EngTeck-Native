package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

@Composable
fun LiveClassJoinCard() {
    var isLive by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var joinMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("liveClasses/default/active")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    isLive = snapshot.getValue(Boolean::class.java) ?: false
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text(
            "LIVE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Color(0xFF1B6B79)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .background(Color(0xFFC0392B), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Live Class", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .border(1.5.dp, Color(0xFFD4A017))
                .padding(vertical = 22.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(if (isLive) Color(0xFFE3F5E9) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    if (isLive) "● Live Now" else "● Not Live Right Now",
                    color = if (isLive) Color(0xFF1F7A3D) else Color(0xFF8A8F99),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Join to hear the teacher live and follow along with shared slides. You can raise your hand any time to ask something.",
                fontSize = 12.5.sp,
                color = Color(0xFF5B5F6B),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Your Name *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(14.dp))
                Text("Your Registered Mobile Number *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { if (it.length <= 10) mobile = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                    )
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (name.isBlank() || mobile.length != 10) {
                        joinMsg = "Please enter your name and a 10-digit mobile number"
                        return@Button
                    }
                    if (!isLive) {
                        joinMsg = "Class is not live right now — please wait for the teacher to start"
                        return@Button
                    }
                    joinMsg = "Sending join request... (Live video system coming soon)"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D4C)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Join Live Class", fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (joinMsg.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(joinMsg, fontSize = 12.sp, color = Color(0xFF946B00), textAlign = TextAlign.Center)
            }
        }
    }
}

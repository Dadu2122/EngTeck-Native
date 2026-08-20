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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.live.AgoraLiveAudio

@Composable
fun AdminLiveClassCard() {
    val context = LocalContext.current
    var isLive by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("liveClasses/default/active")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    isLive = snapshot.getValue(Boolean::class.java) ?: false
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    fun startClass() {
        starting = true
        msg = "Starting class..."
        AgoraLiveAudio.onJoined = {
            starting = false
            connected = true
            msg = "You're live — students can join now."
            FirebaseDatabase.getInstance().getReference("liveClasses/default/active").setValue(true)
        }
        AgoraLiveAudio.onError = { err ->
            starting = false
            msg = "Could not start: $err"
        }
        // Teacher uses a fixed uid (1) so students always find the same broadcaster
        AgoraLiveAudio.join(context, "default", 1)
    }

    fun stopClass() {
        AgoraLiveAudio.leave()
        connected = false
        msg = ""
        FirebaseDatabase.getInstance().getReference("liveClasses/default/active").setValue(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Box(
            modifier = Modifier
                .background(if (isLive) Color(0xFFE3F5E9) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                if (isLive) "● Live Now" else "● Not Live",
                color = if (isLive) Color(0xFF1F7A3D) else Color(0xFF8A8F99),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))

        if (!connected) {
            Text(
                "Start the live class — students will be able to join and hear you as soon as you start.",
                fontSize = 12.5.sp,
                color = Color(0xFF5B5F6B)
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { startClass() },
                enabled = !starting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F7A3D)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (starting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("▶ Start Live Class", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        } else {
            Text("🔊 You are broadcasting", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F7A3D))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        muted = !muted
                        AgoraLiveAudio.setMuted(muted)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (muted) Color(0xFF8A8F99) else Color(0xFF1B6B79)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (muted) "🔇 Unmute" else "🎤 Mute", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { stopClass() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("⏹ Stop Class", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(msg, fontSize = 12.sp, color = Color(0xFF946B00))
        }
    }
}

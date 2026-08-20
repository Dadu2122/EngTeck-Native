package com.shreeyog.engteck.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.live.AgoraLiveAudio

@Composable
fun LiveClassJoinCard() {
    val context = LocalContext.current
    var isLive by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var joinMsg by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var joined by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }

    fun doJoin() {
        joining = true
        joinMsg = "Connecting to live class..."
        val uid = (mobile.ifBlank { "0" }).takeLast(6).toIntOrNull() ?: (1000..999999).random()
        AgoraLiveAudio.onJoined = {
            joining = false
            joined = true
            joinMsg = "Connected — you can hear the teacher now."
        }
        AgoraLiveAudio.onError = { err ->
            joining = false
            joinMsg = "Could not connect: $err — please try again."
        }
        AgoraLiveAudio.join(context, "default", uid)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doJoin()
        else joinMsg = "Mic permission is needed to join the live class."
    }

    fun startJoinFlow() {
        if (name.isBlank() || mobile.length != 10) {
            joinMsg = "Please enter your name and a 10-digit mobile number"
            return
        }
        if (!isLive) {
            joinMsg = "Class is not live right now — please wait for the teacher to start"
            return
        }
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) doJoin() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (joined) AgoraLiveAudio.leave()
            AgoraLiveAudio.onJoined = null
            AgoraLiveAudio.onError = null
        }
    }

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

            if (!joined) {
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
                        singleLine = true,
                        enabled = !joining
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
                        enabled = !joining,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { startJoinFlow() },
                    enabled = !joining,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D4C)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (joining) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Join Live Class", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                Text("🔊 You're connected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F7A3D))
                Spacer(Modifier.height(16.dp))
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
                        onClick = {
                            AgoraLiveAudio.leave()
                            joined = false
                            joinMsg = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Leave Class", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (joinMsg.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(joinMsg, fontSize = 12.sp, color = Color(0xFF946B00), textAlign = TextAlign.Center)
            }
        }
    }
}

package com.shreeyog.engteck.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.live.AgoraLiveAudio
import com.shreeyog.engteck.live.PdfSlideRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Same palette as the teacher's board (AdminLiveClassCard.kt) so both sides look identical.
private val SLIVE_NAVY = Color(0xFF070A12)
private val SLIVE_BOARD_TOP = Color(0xFF12162A)
private val SLIVE_BOARD_BOTTOM = Color(0xFF0A0D1A)
private val SLIVE_GOLD = Color(0xFFD4A017)
private val SLIVE_GREEN = Color(0xFF39FF9E)

// This screen's own outer Column uses padding(horizontal = 22.dp) below — used to
// bleed the board edge-to-edge via offset() (safe; padding() rejects negative values).
private val SCREEN_SIDE_PADDING = 22.dp

// Same gently-pulsing dot as the teacher's board.
@Composable
private fun StudentBlinkingDot(color: Color, size: androidx.compose.ui.unit.Dp = 8.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "studentLiveDotBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "studentLiveDotAlpha"
    )
    Box(Modifier.size(size).background(color.copy(alpha = alpha), CircleShape))
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.StudentCornerBracket(alignment: Alignment) {
    val len = 22.dp
    val thick = 2.dp
    val isTop = alignment == Alignment.TopStart || alignment == Alignment.TopEnd
    val isStart = alignment == Alignment.TopStart || alignment == Alignment.BottomStart
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(
                start = if (isStart) 14.dp else 0.dp,
                end = if (!isStart) 14.dp else 0.dp,
                top = if (isTop) 14.dp else 0.dp,
                bottom = if (!isTop) 14.dp else 0.dp
            )
            .size(len)
    ) {
        Box(Modifier.align(if (isTop) Alignment.TopStart else Alignment.BottomStart).width(len).height(thick).background(SLIVE_GOLD.copy(alpha = 0.75f)))
        Box(Modifier.align(if (isStart) Alignment.TopStart else Alignment.TopEnd).width(thick).height(len).background(SLIVE_GOLD.copy(alpha = 0.75f)))
    }
}

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

    var studentUid by remember { mutableStateOf(0) }
    var slidePdf by remember { mutableStateOf("") }
    var currentPage by remember { mutableStateOf(0) }
    var slideBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var repeatSent by remember { mutableStateOf(false) }
    var participantCount by remember { mutableStateOf(1) } // at least this student

    fun doJoin() {
        joining = true
        joinMsg = "Connecting to live class..."
        val uid = (mobile.ifBlank { "0" }).takeLast(6).toIntOrNull() ?: (1000..999999).random()
        studentUid = uid
        AgoraLiveAudio.onJoined = {
            joining = false
            joined = true
            joinMsg = ""
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

    fun sendRepeatRequest() {
        if (studentUid == 0) return
        repeatSent = true
        FirebaseDatabase.getInstance().getReference("liveClasses/default/repeatRequests")
            .child(studentUid.toString()).setValue(System.currentTimeMillis())
    }

    DisposableEffect(Unit) {
        onDispose {
            if (joined) {
                AgoraLiveAudio.leave()
                if (studentUid != 0) {
                    FirebaseDatabase.getInstance().getReference("liveClasses/default/repeatRequests")
                        .child(studentUid.toString()).removeValue()
                }
            }
            AgoraLiveAudio.onJoined = null
            AgoraLiveAudio.onError = null
            PdfSlideRenderer.close()
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
        FirebaseDatabase.getInstance().getReference("liveClasses/default/slidePdf")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    slidePdf = snapshot.getValue(String::class.java) ?: ""
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/default/currentPage")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    currentPage = snapshot.getValue(Int::class.java) ?: 0
                    repeatSent = false
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    LaunchedEffect(slidePdf, currentPage, joined) {
        if (joined && slidePdf.isNotBlank()) {
            slideBitmap = withContext(Dispatchers.Default) {
                PdfSlideRenderer.renderPage(context, slidePdf, currentPage)
            }
        } else {
            slideBitmap = null
        }
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

        if (!joined) {
            // ---------- Not yet joined: same simple join form as before ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.5.dp, SLIVE_GOLD)
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

                if (joinMsg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(joinMsg, fontSize = 12.sp, color = Color(0xFF946B00), textAlign = TextAlign.Center)
                }
            }
        } else {
            // ---------- Joined: same "Smart Digital Board" look as the teacher's screen ----------
            Column(modifier = Modifier.fillMaxWidth()) {

                Row(
                    modifier = Modifier.fillMaxWidth().background(SLIVE_NAVY).padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudentBlinkingDot(SLIVE_GREEN)
                        Spacer(Modifier.width(7.dp))
                        Text("S.D.BOARD", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 1.5.sp)
                    }
                    Box(
                        modifier = Modifier.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(100.dp))
                            .border(1.dp, SLIVE_GOLD.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text("Connected: $participantCount", color = SLIVE_GOLD, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudentBlinkingDot(SLIVE_GREEN)
                        Spacer(Modifier.width(7.dp))
                        Text("ON_AIR", color = SLIVE_GREEN, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 1.sp)
                    }
                }

                // Full-bleed board: uses offset() (not negative padding — that crashes)
                // to escape this screen's own 22.dp side padding and stretch edge-to-edge.
                // Thin black border, matching the teacher's board.
                val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
                Box(
                    modifier = Modifier
                        .width(screenWidthDp)
                        .offset(x = -SCREEN_SIDE_PADDING)
                        .height(460.dp)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(SLIVE_BOARD_TOP, SLIVE_BOARD_BOTTOM)))
                ) {
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(SLIVE_GOLD.copy(alpha = 0.35f)))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(SLIVE_GOLD.copy(alpha = 0.35f)))
                    StudentCornerBracket(Alignment.TopStart)
                    StudentCornerBracket(Alignment.TopEnd)
                    StudentCornerBracket(Alignment.BottomStart)
                    StudentCornerBracket(Alignment.BottomEnd)
                    if (slideBitmap != null) {
                        Image(
                            bitmap = slideBitmap!!.asImageBitmap(),
                            contentDescription = "Slide",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                        )
                    } else if (slidePdf.isNotBlank()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SLIVE_GOLD)
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("🔊 YOU'RE CONNECTED", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SLIVE_GREEN, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 1.sp)
                        }
                    }
                }

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
                        onClick = { sendRepeatRequest() },
                        enabled = !repeatSent,
                        colors = ButtonDefaults.buttonColors(containerColor = if (repeatSent) Color(0xFF8A8F99) else SLIVE_GOLD),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (repeatSent) "🔁 Sent" else "🔁 Please Repeat", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            AgoraLiveAudio.leave()
                            if (studentUid != 0) {
                                FirebaseDatabase.getInstance().getReference("liveClasses/default/repeatRequests")
                                    .child(studentUid.toString()).removeValue()
                            }
                            joined = false
                            joinMsg = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Leave", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                if (joinMsg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(joinMsg, fontSize = 12.sp, color = Color(0xFF946B00), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

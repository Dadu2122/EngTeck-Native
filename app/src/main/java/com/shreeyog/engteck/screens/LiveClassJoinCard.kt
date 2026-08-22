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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
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

// Side padding used for non-board sections (header, join form, buttons).
// The board itself no longer needs this — it's a direct fillMaxWidth child
// of the root Column, which has no padding of its own, so it's naturally
// edge-to-edge with zero offset math needed.
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

    // Multiple teachers can run separate live classes at once — this picks which one.
    var allTeachers by remember { mutableStateOf<List<TeacherEntry>>(emptyList()) }
    var liveMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var selectedTeacherKey by remember { mutableStateOf("") }
    var selectedTeacherName by remember { mutableStateOf("") }

    // Two-finger pinch-zoom + pan on the slide, same behaviour as the teacher's board.
    var zoomScale by remember { mutableStateOf(1f) }
    var zoomOffsetX by remember { mutableStateOf(0f) }
    var zoomOffsetY by remember { mutableStateOf(0f) }
    var boardSizePx by remember { mutableStateOf(IntSize.Zero) }

    // "Knock to join" — student's request sits here until the teacher approves it.
    var requestKey by remember { mutableStateOf("") }
    var requestStatus by remember { mutableStateOf("") } // "", "pending", "approved", "rejected"

    fun doJoin() {
        joining = true
        joinMsg = "Connecting to live class..."
        val uid = (mobile.ifBlank { "0" }).takeLast(6).toIntOrNull() ?: (1000..999999).random()
        studentUid = uid
        AgoraLiveAudio.onJoined = {
            joining = false
            joined = true
            joinMsg = ""
            // Presence record so the teacher can see who's actually connected right now.
            FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/participants")
                .child(uid.toString())
                .setValue(mapOf("name" to name, "mobile" to mobile, "joinedAt" to System.currentTimeMillis()))
        }
        AgoraLiveAudio.onError = { err ->
            joining = false
            joinMsg = "Could not connect: $err — please try again."
        }
        AgoraLiveAudio.join(context, "live_$selectedTeacherKey", uid)
    }

    fun cancelJoinRequest() {
        if (requestKey.isNotEmpty() && selectedTeacherKey.isNotEmpty()) {
            FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/joinRequests")
                .child(requestKey).removeValue()
        }
        requestKey = ""
        requestStatus = ""
        joinMsg = ""
    }

    fun sendJoinRequest() {
        requestStatus = "pending"
        joinMsg = ""
        val ref = FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/joinRequests").push()
        requestKey = ref.key ?: ""
        ref.setValue(
            mapOf(
                "name" to name,
                "mobile" to mobile,
                "timestamp" to System.currentTimeMillis(),
                "status" to "pending"
            )
        )
        ref.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java) ?: return
                requestStatus = status
                when (status) {
                    "approved" -> {
                        snapshot.ref.removeValue()
                        requestKey = ""
                        doJoin()
                    }
                    "rejected" -> {
                        joinMsg = "Teacher ne abhi request accept nahi ki — thodi der baad try karein."
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendJoinRequest()
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
        if (hasPermission) sendJoinRequest() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun sendRepeatRequest() {
        if (studentUid == 0) return
        repeatSent = true
        FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/repeatRequests")
            .child(studentUid.toString()).setValue(System.currentTimeMillis())
    }

    DisposableEffect(Unit) {
        onDispose {
            if (joined) {
                AgoraLiveAudio.leave()
                if (studentUid != 0) {
                    FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/repeatRequests")
                        .child(studentUid.toString()).removeValue()
                    FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/participants")
                        .child(studentUid.toString()).removeValue()
                }
            }
            if (requestKey.isNotEmpty() && selectedTeacherKey.isNotEmpty()) {
                FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/joinRequests")
                    .child(requestKey).removeValue()
            }
            AgoraLiveAudio.onJoined = null
            AgoraLiveAudio.onError = null
            PdfSlideRenderer.close()
        }
    }

    // Fetch all teachers once, then track each one's live/active status —
    // this powers the "who's live right now" picker shown before a teacher is chosen.
    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("teachers").get()
            .addOnSuccessListener { snapshot ->
                allTeachers = snapshot.children.mapNotNull { c ->
                    val n = c.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    TeacherEntry(c.key ?: "", n, "")
                }
            }
    }
    allTeachers.forEach { t ->
        DisposableEffect(t.key) {
            val ref = FirebaseDatabase.getInstance().getReference("liveClasses/${t.key}/active")
            val listener = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    liveMap = liveMap + (t.key to (snapshot.getValue(Boolean::class.java) ?: false))
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            }
            ref.addValueEventListener(listener)
            onDispose { ref.removeEventListener(listener) }
        }
    }

    // Once a teacher is picked, follow that specific teacher's live class data.
    LaunchedEffect(selectedTeacherKey) {
        if (selectedTeacherKey.isEmpty()) return@LaunchedEffect
        FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/active")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    isLive = snapshot.getValue(Boolean::class.java) ?: false
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/slidePdf")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    slidePdf = snapshot.getValue(String::class.java) ?: ""
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/currentPage")
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
            try {
                slideBitmap = withContext(Dispatchers.Default) {
                    PdfSlideRenderer.renderPage(context, slidePdf, currentPage)
                }
            } catch (e: Exception) {
                android.util.Log.e("SDBoard", "PDF render failed", e)
                slideBitmap = null
                joinMsg = "Could not load the shared slide."
            }
        } else {
            slideBitmap = null
        }
        // Reset zoom whenever the page changes so the student doesn't stay zoomed-in on the next slide.
        zoomScale = 1f; zoomOffsetX = 0f; zoomOffsetY = 0f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
        }
        Spacer(Modifier.height(14.dp))

        if (!joined) {
            if (selectedTeacherKey.isEmpty()) {
                // ---------- Step 1: pick which teacher's live class to join ----------
                val liveTeachers = allTeachers.filter { liveMap[it.key] == true }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .background(Color.White)
                        .border(1.5.dp, SLIVE_GOLD)
                        .padding(vertical = 22.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (liveTeachers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("● Not Live Right Now", color = Color(0xFF8A8F99), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Koi bhi teacher abhi live nahi hai. Class shuru hote hi yahan dikhega.",
                            fontSize = 12.5.sp, color = Color(0xFF5B5F6B), textAlign = TextAlign.Center, lineHeight = 18.sp
                        )
                    } else {
                        Text("Abhi Live Classes", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                        Spacer(Modifier.height(14.dp))
                        liveTeachers.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .background(Color(0xFFF5F3EC), RoundedCornerShape(12.dp))
                                    .clickable { selectedTeacherKey = t.key; selectedTeacherName = t.name }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(8.dp).background(Color(0xFF1F7A3D), CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(t.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D), modifier = Modifier.weight(1f))
                                Text("Join ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE85D4C))
                            }
                        }
                    }
                }
            } else {
            // ---------- Step 2: same simple join form as before, scoped to the chosen teacher ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .background(Color.White)
                    .border(1.5.dp, SLIVE_GOLD)
                    .padding(vertical = 22.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(onClick = { selectedTeacherKey = ""; joinMsg = "" }, modifier = Modifier.align(Alignment.Start)) {
                    Text("‹ Change Teacher", fontSize = 11.5.sp, color = Color(0xFF5B5F6B))
                }
                Text(selectedTeacherName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                Spacer(Modifier.height(8.dp))
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
                if (requestStatus == "pending") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFCF3D9), RoundedCornerShape(14.dp))
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF946B00), modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("Teacher ki approval ka wait ho raha hai…", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF946B00), textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { cancelJoinRequest() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel Request", color = Color(0xFFC0392B))
                    }
                } else {
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
                }

                if (joinMsg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(joinMsg, fontSize = 12.sp, color = Color(0xFF946B00), textAlign = TextAlign.Center)
                }
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

                // Plain fillMaxWidth — no offset/measuring needed anymore, since
                // the root Column above no longer wraps this section in any
                // padding (HomeScreen.kt confirmed there's none above it either).
                // Pinch-to-zoom + pan via graphicsLayer, same pattern as the teacher's board.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(560.dp)
                        .background(Color.White)
                        .border(0.75.dp, Color.Black)
                        .clip(androidx.compose.ui.graphics.RectangleShape)
                        .onSizeChanged { boardSizePx = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoomScale = (zoomScale * gestureZoom).coerceIn(1f, 4f)
                                val maxOffsetX = (boardSizePx.width * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxOffsetY = (boardSizePx.height * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                                zoomOffsetX = (zoomOffsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                zoomOffsetY = (zoomOffsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            }
                        }
                ) {
                    // Zoom/pan applied only to this inner layer — the outer Box's
                    // border above stays fixed size, so it never thickens into a
                    // visible line across the slide when zoomScale > 1.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale, scaleY = zoomScale,
                                translationX = zoomOffsetX, translationY = zoomOffsetY
                            )
                    ) {
                        if (slideBitmap != null) {
                            Image(
                                bitmap = slideBitmap!!.asImageBitmap(),
                                contentDescription = "Slide",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (slidePdf.isNotBlank()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = SLIVE_NAVY)
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("🔊 You're connected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F7A3D))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                // fillMaxWidth + weight(1f) on each button so all three (Mute,
                // Please Repeat, Leave) always fit within the screen width —
                // nothing gets pushed off-screen anymore.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            muted = !muted
                            AgoraLiveAudio.setMuted(muted)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (muted) Color(0xFF8A8F99) else Color(0xFF1B6B79)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(if (muted) "🔇 Unmute" else "🎤 Mute", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { sendRepeatRequest() },
                        enabled = !repeatSent,
                        colors = ButtonDefaults.buttonColors(containerColor = if (repeatSent) Color(0xFF8A8F99) else SLIVE_GOLD),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(if (repeatSent) "🔁 Sent" else "🔁 Repeat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            AgoraLiveAudio.leave()
                            if (studentUid != 0) {
                                FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/repeatRequests")
                                    .child(studentUid.toString()).removeValue()
                                FirebaseDatabase.getInstance().getReference("liveClasses/$selectedTeacherKey/participants")
                                    .child(studentUid.toString()).removeValue()
                            }
                            joined = false
                            joinMsg = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Leave", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                }

                if (joinMsg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(joinMsg, fontSize = 12.sp, color = Color(0xFF946B00), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

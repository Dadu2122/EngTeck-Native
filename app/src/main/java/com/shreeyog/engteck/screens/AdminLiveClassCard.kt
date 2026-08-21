package com.shreeyog.engteck.screens

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.live.AgoraLiveAudio
import com.shreeyog.engteck.live.AnnotationCanvas
import com.shreeyog.engteck.live.AnnotationTool
import com.shreeyog.engteck.live.InkShape
import com.shreeyog.engteck.live.PdfSlideRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LIVE_NAVY = Color(0xFF0E1420)
private val LIVE_GOLD = Color(0xFFD4A017)
private val LIVE_GREEN = Color(0xFF4CD980)

// Side padding of the parent screen this card sits in — used to make the board
// bleed edge-to-edge via offset() (which allows negative values safely, unlike
// padding() which crashes on negative numbers).
private val SCREEN_SIDE_PADDING = 16.dp

private enum class BoardMode { PDF, PASTE_TEXT, WHITEBOARD }

private val TOOL_COLORS = listOf(
    Color(0xFFC0392B), Color(0xFF12203D), Color(0xFF1F7A3D), Color(0xFF1B6B79), Color(0xFFE85D4C)
)

// A live-status dot that gently pulses (fades in/out) instead of sitting static —
// signals "actively live" the way a recording/broadcast indicator usually does.
@Composable
private fun BlinkingDot(color: Color, size: androidx.compose.ui.unit.Dp = 8.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "liveDotBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveDotAlpha"
    )
    Box(Modifier.size(size).background(color.copy(alpha = alpha), CircleShape))
}

@Composable
fun AdminLiveClassCard() {
    val context = LocalContext.current
    var isLive by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    var boardMode by remember { mutableStateOf(BoardMode.PDF) }
    var slidePdf by remember { mutableStateOf("") }
    var pastedText by remember { mutableStateOf("") }
    var pastedTextDraft by remember { mutableStateOf("") }
    var pasteEditing by remember { mutableStateOf(true) } // true = input box open, false = collapsed
    var currentPage by remember { mutableStateOf(0) }
    var pageCount by remember { mutableStateOf(0) }
    var slideBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var repeatCount by remember { mutableStateOf(0) }
    var participantCount by remember { mutableStateOf(0) }

    var tool by remember { mutableStateOf(AnnotationTool.POINTER) }
    var penColor by remember { mutableStateOf(TOOL_COLORS[0]) }
    var penWidth by remember { mutableStateOf(6f) }
    val strokes = remember { mutableStateListOf<InkShape>() }
    val redoStack = remember { mutableStateListOf<InkShape>() }

    // Two-finger pinch-zoom + pan. Uses Initial pass and only reacts to 2+
    // pointers, so single-finger tool touches are never intercepted.
    var zoomScale by remember { mutableStateOf(1f) }
    var zoomOffsetX by remember { mutableStateOf(0f) }
    var zoomOffsetY by remember { mutableStateOf(0f) }
    var boardSizePx by remember { mutableStateOf(IntSize.Zero) }
    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploading = true
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val b64 = "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    FirebaseDatabase.getInstance().getReference("liveClasses/default/slidePdf").setValue(b64)
                    FirebaseDatabase.getInstance().getReference("liveClasses/default/currentPage").setValue(0)
                    msg = "Slides shared with the class."
                } else msg = "Could not read that file."
            } catch (e: Exception) {
                msg = "Upload failed: ${e.message}"
            }
            uploading = false
        }
    }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("liveClasses/default/active")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) { isLive = s.getValue(Boolean::class.java) ?: false }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/default/slidePdf")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) { slidePdf = s.getValue(String::class.java) ?: "" }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/default/currentPage")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    currentPage = s.getValue(Int::class.java) ?: 0
                    strokes.clear(); redoStack.clear()
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/default/repeatRequests")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) { repeatCount = s.childrenCount.toInt() }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
    }

    LaunchedEffect(slidePdf, currentPage) {
        if (slidePdf.isNotBlank()) {
            val result = withContext(Dispatchers.Default) {
                Pair(PdfSlideRenderer.pageCount(context, slidePdf), PdfSlideRenderer.renderPage(context, slidePdf, currentPage))
            }
            pageCount = result.first
            slideBitmap = result.second
        } else {
            slideBitmap = null; pageCount = 0
        }
        // Reset zoom whenever the page changes so you don't stay zoomed-in on the next slide.
        zoomScale = 1f; zoomOffsetX = 0f; zoomOffsetY = 0f
    }

    fun startClass() {
        starting = true
        msg = "Starting class..."
        AgoraLiveAudio.onJoined = {
            starting = false; connected = true; msg = ""
            FirebaseDatabase.getInstance().getReference("liveClasses/default/active").setValue(true)
        }
        AgoraLiveAudio.onUserJoined = { participantCount++ }
        AgoraLiveAudio.onUserLeft = { if (participantCount > 0) participantCount-- }
        AgoraLiveAudio.onError = { err -> starting = false; msg = "Could not start: $err" }
        AgoraLiveAudio.join(context, "default", 1)
    }

    fun stopClass() {
        AgoraLiveAudio.leave()
        connected = false; msg = ""; participantCount = 0
        FirebaseDatabase.getInstance().getReference("liveClasses/default/active").setValue(false)
        FirebaseDatabase.getInstance().getReference("liveClasses/default/repeatRequests").removeValue()
    }

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        FirebaseDatabase.getInstance().getReference("liveClasses/default/currentPage").setValue(clamped)
    }

    if (!connected) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
                .border(1.5.dp, LIVE_GOLD, RoundedCornerShape(16.dp)).padding(18.dp)
        ) {
            Box(modifier = Modifier.background(if (isLive) Color(0xFFE3F5E9) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text(if (isLive) "● Live Now" else "● Not Live", color = if (isLive) Color(0xFF1F7A3D) else Color(0xFF8A8F99), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Text("Start the live class — students will be able to join and hear you as soon as you start.", fontSize = 12.5.sp, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { startClass() }, enabled = !starting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F7A3D)),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (starting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("▶ Start Live Class", fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (msg.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(msg, fontSize = 12.sp, color = Color(0xFF946B00)) }
        }
        return
    }

    // ---------- Connected: full dark "Smart Digital Board" layout ----------
    Column(modifier = Modifier.fillMaxWidth()) {

        // Dark status strip: S.D. BOARD | Connected: N | ON AIR
        Row(
            modifier = Modifier.fillMaxWidth().background(LIVE_NAVY).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BlinkingDot(LIVE_GREEN)
                Spacer(Modifier.width(6.dp))
                Text("S.D. BOARD", color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(100.dp))
                    .border(1.dp, LIVE_GOLD.copy(alpha = 0.6f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text("Connected: $participantCount", color = LIVE_GOLD, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BlinkingDot(LIVE_GREEN)
                Spacer(Modifier.width(6.dp))
                Text("ON AIR", color = LIVE_GREEN, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Board is now the real website board, loaded in a WebView — same
        // full-bleed layout, same annotation tools, same PDF rendering as
        // index.html, since it IS index.html. Native audio (Agora) below
        // keeps working independently; this only replaces the visual board.
        // ?embed=board hides the site's header/join-form and forces the
        // board visible — pure CSS-level, doesn't touch the site's audio JS.
        com.shreeyog.engteck.live.LiveClassBoardWebView(
            url = "https://dadu2122.github.io/Shree-English-Classes/?embed=board#liveClassSection",
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp)
                .background(Color.White)
                .border(0.75.dp, Color.Black)
        )

        // Everything below (page-nav, mode switch, annotation tools, colour,
        // pen width, PDF upload, paste-text box) is removed here — the
        // WebView board above already contains all of that, since it's the
        // real website UI. Only native audio controls (Mute/Stop) remain.
        Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {

            if (repeatCount > 0) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFCF3D9), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("🔁 $repeatCount student(s) asked you to repeat", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF946B00))
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { muted = !muted; AgoraLiveAudio.setMuted(muted) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (muted) Color(0xFF8A8F99) else Color(0xFF1B6B79)),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                ) { Text(if (muted) "🔇 Unmute" else "🎤 Mute", color = Color.White, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { stopClass() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                ) { Text("⏹ Stop Class", color = Color.White, fontWeight = FontWeight.Bold) }
            }

            if (msg.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(msg, fontSize = 12.sp, color = Color(0xFF946B00), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

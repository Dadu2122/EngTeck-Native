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

        // Big board — pure white. Width kept simple (fillMaxWidth) after the
        // earlier full-bleed offset trick broke the layout — reverted for safety.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp)
                .background(Color.White)
                .border(3.dp, LIVE_GOLD)
                .onSizeChanged { boardSizePx = it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoomScale, scaleY = zoomScale,
                        translationX = zoomOffsetX, translationY = zoomOffsetY
                    )
            ) {
                when (boardMode) {
                    BoardMode.PDF -> {
                        if (slideBitmap != null) {
                            Image(bitmap = slideBitmap!!.asImageBitmap(), contentDescription = "Slide", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (slidePdf.isBlank()) "No PDF shared yet." else "Loading...", fontSize = 12.sp, color = Color(0xFF8A8F99))
                            }
                        }
                    }
                    BoardMode.PASTE_TEXT -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp)
                        ) {
                            Text(pastedText.ifBlank { "Paste text below to show it here." }, fontSize = 15.sp, color = Color(0xFF1A1A1A))
                        }
                    }
                    BoardMode.WHITEBOARD -> {
                        Box(Modifier.fillMaxSize())
                    }
                }
                AnnotationCanvas(
                    modifier = Modifier.fillMaxSize(),
                    tool = tool, color = penColor, penWidth = penWidth,
                    strokes = strokes, redoStack = redoStack,
                    swipeEnabled = boardMode == BoardMode.PDF,
                    onSwipeLeft = { if (currentPage < pageCount - 1) goToPage(currentPage + 1) },
                    onSwipeRight = { if (currentPage > 0) goToPage(currentPage - 1) },
                    onZoomPan = { zoomChange, panChange ->
                        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
                        val maxOffsetX = (boardSizePx.width * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                        val maxOffsetY = (boardSizePx.height * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                        zoomOffsetX = (zoomOffsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                        zoomOffsetY = (zoomOffsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                    }
                )
            }
        }

        // Dark page-nav strip under the board
        Row(
            modifier = Modifier.fillMaxWidth().background(LIVE_NAVY).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (boardMode == BoardMode.PDF && pageCount > 0) {
                Box(
                    modifier = Modifier.background(Color.Transparent, RoundedCornerShape(10.dp))
                        .border(1.5.dp, LIVE_GOLD, RoundedCornerShape(10.dp))
                        .clickable(enabled = currentPage > 0) { goToPage(currentPage - 1) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("‹", color = LIVE_GOLD, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("${currentPage + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Text("/", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("$pageCount", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier.background(Color.Transparent, RoundedCornerShape(10.dp))
                        .border(1.5.dp, LIVE_GOLD, RoundedCornerShape(10.dp))
                        .clickable(enabled = currentPage < pageCount - 1) { goToPage(currentPage + 1) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("›", color = LIVE_GOLD, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            } else {
                Text("No page controls in this mode", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {

            if (repeatCount > 0) {
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFCF3D9), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("🔁 $repeatCount student(s) asked you to repeat", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF946B00))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Mode switch: PDF / Paste Text / Whiteboard — ONLY switches, does nothing else.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple(BoardMode.PDF, "📄", "PDF"), Triple(BoardMode.PASTE_TEXT, "📜", "Paste Text"), Triple(BoardMode.WHITEBOARD, "✏️", "Whiteboard")).forEach { (mode, icon, label) ->
                    Box(
                        modifier = Modifier.weight(1f)
                            .background(LIVE_NAVY, RoundedCornerShape(100.dp))
                            .border(if (boardMode == mode) 1.5.dp else 0.dp, LIVE_GOLD, RoundedCornerShape(100.dp))
                            .clickable {
                                boardMode = mode
                                if (mode == BoardMode.PASTE_TEXT && pastedText.isBlank()) pasteEditing = true
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$icon $label", color = if (boardMode == mode) LIVE_GOLD else Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Stylish dark tool row
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.5.dp, LIVE_GOLD, RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(
                        AnnotationTool.POINTER to "👆", AnnotationTool.MOVE to "✋", AnnotationTool.MARKER to "✏️", AnnotationTool.HIGHLIGHTER to "🖍️",
                        AnnotationTool.ERASER to "🧹", AnnotationTool.RECTANGLE to "▭", AnnotationTool.CIRCLE to "○",
                        AnnotationTool.LINE to "➖", AnnotationTool.ARROW to "➡️"
                    )) { (t, icon) ->
                        val active = tool == t
                        Box(
                            modifier = Modifier.background(if (active) LIVE_NAVY else Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                .clickable { tool = t }.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) { Text(icon, fontSize = 16.sp) }
                    }
                    item {
                        Box(
                            modifier = Modifier.background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                .clickable { if (strokes.isNotEmpty()) { redoStack.add(strokes.removeAt(strokes.size - 1)) } }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) { Text("↩ Undo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A)) }
                    }
                    item {
                        Box(
                            modifier = Modifier.background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                .clickable { if (redoStack.isNotEmpty()) { strokes.add(redoStack.removeAt(redoStack.size - 1)) } }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) { Text("↪ Redo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A)) }
                    }
                    item {
                        Box(
                            modifier = Modifier.background(Color(0xFFFBE0DE), RoundedCornerShape(10.dp))
                                .clickable { strokes.clear(); redoStack.clear() }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) { Text("🧹 Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC0392B)) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Colour:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5F6B))
                TOOL_COLORS.forEach { c ->
                    Box(
                        modifier = Modifier.size(28.dp).background(c, CircleShape)
                            .border(if (penColor == c) 3.dp else 0.dp, Color(0xFF1A1A1A), CircleShape)
                            .clickable { penColor = c }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Thin" to 3f, "Medium" to 6f, "Thick" to 12f).forEach { (label, w) ->
                    Box(
                        modifier = Modifier.background(if (penWidth == w) LIVE_GOLD else Color(0xFFF5F3EC), RoundedCornerShape(100.dp))
                            .clickable { penWidth = w }.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (penWidth == w) Color.White else Color(0xFF5B5F6B)) }
                }
            }

            Spacer(Modifier.height(16.dp))
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

            // ---- Below Mute/Stop: PDF upload AND Paste+Save always together here,
            // regardless of which mode the top switcher currently shows. ----
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { pdfPickerLauncher.launch("application/pdf") }, enabled = !uploading,
                colors = ButtonDefaults.buttonColors(containerColor = LIVE_GOLD), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                if (uploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (slidePdf.isBlank()) "📂 Share PDF Files" else "📂 Change PDF Files", color = Color(0xFF12203D), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))

            if (pasteEditing) {
                OutlinedTextField(
                    value = pastedTextDraft, onValueChange = { pastedTextDraft = it },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    placeholder = { Text("Paste text to show on board...") }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        pastedText = pastedTextDraft
                        pasteEditing = false // collapse after saving
                        boardMode = BoardMode.PASTE_TEXT // auto-show it on the board
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LIVE_GOLD),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) { Text("💾 Save to Board", color = Color(0xFF12203D), fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = {
                        pastedTextDraft = pastedText
                        pasteEditing = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F3EC)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) { Text(if (pastedText.isBlank()) "📜 Paste Text" else "✏️ Edit Text", color = Color(0xFF12203D), fontWeight = FontWeight.Bold) }
            }

            if (msg.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(msg, fontSize = 12.sp, color = Color(0xFF946B00), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

package com.shreeyog.engteck.screens

import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LIVE_NAVY = Color(0xFF070A12)
private val LIVE_BOARD_TOP = Color(0xFF12162A)
private val LIVE_BOARD_BOTTOM = Color(0xFF0A0D1A)
private val LIVE_GOLD = Color(0xFFD4A017)
private val LIVE_GREEN = Color(0xFF39FF9E)

// Side padding of the parent screen this card sits in — used to make the board
// bleed edge-to-edge, escaping AdminPanelScreen's fixed 16dp padding.
private val SCREEN_SIDE_PADDING = 16.dp

// Widens the composable to cover the parent's side padding and shifts it left
// to align flush with the real screen edge — a proper full-bleed technique
// (unlike offset() alone, this actually changes the measured width too).
private fun Modifier.fullBleed(padding: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val extraPx = (padding * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extraPx))
    layout(placeable.width, placeable.height) {
        placeable.place(-padding.roundToPx(), 0)
    }
}

// Saves a bitmap straight into the phone's public Gallery (Pictures/EngTeck)
// via MediaStore — shows up immediately in the Gallery/Photos app, no share
// sheet needed, and no storage permission required on Android 10+.
private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val filename = "board_snapshot_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EngTeck")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (e: Exception) {
        false
    }
}

private enum class BoardMode { PDF, PASTE_TEXT, WHITEBOARD, MCQ }

// question, options, index of the correct option, optional explanation shown after Reveal
private data class McqItem(val question: String, val options: List<String>, val correctIndex: Int, val explanation: String = "")

// One classroom-chat message — shared shape with the student side.
private data class ChatMessage(val id: String, val senderId: String, val senderName: String, val text: String, val timestamp: Long, val isTeacher: Boolean)

private val TOOL_COLORS = listOf(
    Color(0xFFC0392B), Color(0xFF12203D), Color(0xFF1F7A3D), Color(0xFF1B6B79), Color(0xFFE85D4C)
)

private val LIVE_ORANGE = Color(0xFFE8734C)
private val LIVE_MAROON = Color(0xFF8E2A3B)
private val CHART_COLORS = listOf(Color(0xFF1F7A3D), Color(0xFFC0392B), Color(0xFFD4A017), Color(0xFF1B6B79))

// EDIT THIS: base URL students use to join a live class. Point it at your
// actual deep-link / web join page before shipping.
private const val CLASS_JOIN_BASE_URL = "https://shreeyogapp.com/join"

// EDIT THIS: your backend endpoint that proxies the AI MCQ-generation call.
// Never call a third-party AI API with a bare API key from inside the app —
// route it through your own server, which is what this URL should point to.
private const val AI_GENERATE_MCQ_ENDPOINT = "https://your-backend.example.com/api/generate-mcq"

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

// L-shaped glowing corner bracket for the Command Deck board frame — built from
// two thin bars rather than a partial border (Compose borders are always
// four-sided), positioned per corner via BoxScope.align.
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CornerBracket(alignment: Alignment) {
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
        Box(Modifier.align(if (isTop) Alignment.TopStart else Alignment.BottomStart).width(len).height(thick).background(LIVE_GOLD.copy(alpha = 0.75f)))
        Box(Modifier.align(if (isStart) Alignment.TopStart else Alignment.TopEnd).width(thick).height(len).background(LIVE_GOLD.copy(alpha = 0.75f)))
    }
}

// Capsule section header used above each group of board-tool rows
// (MCQ ON BOARD / LIVE POLL ON BOARD / TOOLS), matching the WebView pills.
@Composable
private fun SectionPill(label: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(100.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

// Full-width outlined row button used under each section pill.
@Composable
private fun SectionRow(label: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LIVE_NAVY, RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(label, color = accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

// Floating card shown on top of the board while a poll is active — bar chart
// of live vote percentages, matching the WebView "LIVE POLL" card.
@Composable
private fun PollOverlayCard(question: String, options: List<String>, votes: Map<String, Int>, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val counts = options.indices.map { idx -> votes.values.count { it == idx } }
    val total = counts.sum()
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFFEAE4D3), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(LIVE_ORANGE, Color(0xFFF0A94E))))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BlinkingDot(Color.White)
                Spacer(Modifier.width(8.dp))
                Text("LIVE POLL", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Text("✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onEnd))
            }
            Spacer(Modifier.height(6.dp))
            Text(question, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                options.forEachIndexed { idx, _ ->
                    val pct = if (total > 0) counts[idx] * 100 / total else 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height((pct.coerceAtLeast(2) * 0.9f).dp)
                                .background(CHART_COLORS[idx % CHART_COLORS.size], RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                options.forEach { opt -> Text(opt, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            Spacer(Modifier.height(10.dp))
            Text("$total students voted", fontSize = 11.5.sp, color = Color(0xFF8A8F99), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

// MCQ shown as the board content itself (BoardMode.MCQ) — matches the WebView
// reference exactly: numbered question, "N responses", A/B/C/D option cards,
// a Reveal/Hide toggle that recolors options green/red, and a dashed
// explanation box once revealed.
@Composable
private fun McqBoardView(
    queueIndex: Int, question: String, options: List<String>, correctIndex: Int, explanation: String,
    revealAnswer: Boolean, votes: Map<String, Int>,
    onToggleReveal: () -> Unit, queueSize: Int, onPrevQueued: () -> Unit, onNextQueued: () -> Unit,
    modifier: Modifier = Modifier
) {
    val counts = options.indices.map { idx -> votes.values.count { it == idx } }
    val total = counts.sum()
    val letters = listOf("A", "B", "C", "D")
    Column(
        modifier = modifier
            .background(Color(0xFFFBF8F1))
            .padding(22.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "${queueIndex + 1}. ${question.ifBlank { "No MCQ shared yet." }}",
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A)
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("📦", fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("$total response${if (total == 1) "" else "s"}", fontSize = 13.sp, color = Color(0xFF5B5F6B))
        }
        Spacer(Modifier.height(14.dp))
        options.forEachIndexed { idx, opt ->
            val pct = if (total > 0) counts[idx] * 100 / total else 0
            val isCorrect = revealAnswer && idx == correctIndex
            val isWrongRevealed = revealAnswer && idx != correctIndex
            val bg = when { isCorrect -> Color(0xFFE3F5E9); isWrongRevealed -> Color(0xFFFBE6E4); else -> Color.White }
            val border = when { isCorrect -> Color(0xFF1F7A3D); isWrongRevealed -> Color(0xFFC0392B); else -> Color(0xFFE7E2D4) }
            val circleColor = when { isCorrect -> Color(0xFF1F7A3D); isWrongRevealed -> Color(0xFFC0392B); else -> Color(0xFFEAE4D3) }
            val circleTextColor = if (revealAnswer) Color.White else Color(0xFF12203D)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .background(bg, RoundedCornerShape(16.dp))
                    .border(1.5.dp, border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(30.dp).background(circleColor, CircleShape), contentAlignment = Alignment.Center) {
                    Text(letters.getOrElse(idx) { "?" }, color = circleTextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Text(opt, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                Text("$pct%", fontSize = 14.sp, color = Color(0xFF8A8F99))
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(100.dp))
                    .border(1.5.dp, if (revealAnswer) Color(0xFF1F7A3D) else Color(0xFFE85D4C), RoundedCornerShape(100.dp))
                    .clickable(onClick = onToggleReveal)
                    .padding(horizontal = 26.dp, vertical = 12.dp)
            ) {
                Text(
                    if (revealAnswer) "🙈  Hide" else "✅  Reveal",
                    color = if (revealAnswer) Color(0xFF1F7A3D) else Color(0xFFE85D4C),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        if (revealAnswer && explanation.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFCF3D9), RoundedCornerShape(14.dp))
                    .border(1.5.dp, LIVE_GOLD, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Text(explanation, fontSize = 14.sp, color = Color(0xFF3A3A3A), lineHeight = 20.sp)
            }
        }
        if (queueSize > 1) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onPrevQueued, enabled = queueIndex > 0, modifier = Modifier.weight(1f)) { Text("‹ Prev") }
                OutlinedButton(onClick = onNextQueued, enabled = queueIndex < queueSize - 1, modifier = Modifier.weight(1f)) { Text("Next ›") }
            }
            Spacer(Modifier.height(8.dp))
            Text("MCQ ${queueIndex + 1} of $queueSize", fontSize = 11.sp, color = Color(0xFF8A8F99))
        }
    }
}

@Composable
private fun CreatePollDialog(onDismiss: () -> Unit, onLaunch: (String, List<String>) -> Unit) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("📊 Create Live Poll", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(16.dp))
            Text("Question", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Who will win today?") })
            Spacer(Modifier.height(16.dp))
            Text("Options (2 to 4)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(6.dp))
            options.forEachIndexed { idx, value ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { new -> options = options.toMutableList().also { it[idx] = new } },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Option ${idx + 1}") }
                    )
                    if (options.size > 2) {
                        Spacer(Modifier.width(6.dp))
                        Text("✕", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold, modifier = Modifier.clickable { options = options.toMutableList().also { it.removeAt(idx) } })
                    }
                }
            }
            if (options.size < 4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, LIVE_GOLD, RoundedCornerShape(10.dp))
                        .clickable { options = options + "" }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+ Add Option (max 4)", color = LIVE_GOLD, fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                val cleanOptions = options.map { it.trim() }.filter { it.isNotBlank() }
                Button(
                    onClick = { if (question.isNotBlank() && cleanOptions.size >= 2) onLaunch(question.trim(), cleanOptions) },
                    enabled = question.isNotBlank() && cleanOptions.size >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = LIVE_ORANGE),
                    modifier = Modifier.weight(1f)
                ) { Text("🚀 Launch Poll", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CreateMcqDialog(onDismiss: () -> Unit, onLaunch: (String, List<String>, Int, String) -> Unit) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "", "", "")) }
    var correctIndex by remember { mutableStateOf(0) }
    var explanation by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("📝 Create One MCQ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Question") })
            Spacer(Modifier.height(14.dp))
            Text("Tap the circle next to the correct option", fontSize = 11.5.sp, color = Color(0xFF8A8F99))
            Spacer(Modifier.height(8.dp))
            options.forEachIndexed { idx, value ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    RadioButton(selected = correctIndex == idx, onClick = { correctIndex = idx }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF1F7A3D)))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { new -> options = options.toMutableList().also { it[idx] = new } },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Option ${('A' + idx)}") }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Explanation (shown after Reveal, optional)", fontSize = 11.5.sp, color = Color(0xFF8A8F99))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = explanation, onValueChange = { explanation = it },
                modifier = Modifier.fillMaxWidth().height(70.dp),
                placeholder = { Text("e.g. Shakespeare was the greatest dramatist of 16th century.") }
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                val cleanOptions = options.map { it.trim() }
                val valid = question.isNotBlank() && cleanOptions.count { it.isNotBlank() } >= 2 && cleanOptions.getOrNull(correctIndex)?.isNotBlank() == true
                Button(
                    onClick = { if (valid) onLaunch(question.trim(), cleanOptions.filter { it.isNotBlank() }, correctIndex, explanation.trim()) },
                    enabled = valid,
                    colors = ButtonDefaults.buttonColors(containerColor = LIVE_GOLD),
                    modifier = Modifier.weight(1f)
                ) { Text("🚀 Launch on Board", color = Color(0xFF12203D), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// Draft state for one question in the Bulk Add editor — mutable via copy(),
// same immutable-list-of-drafts pattern used elsewhere in this file.
private data class McqDraft(
    val question: String = "",
    val optionA: String = "", val optionB: String = "", val optionC: String = "", val optionD: String = "",
    val correctLetter: String = "A",
    val explanation: String = ""
)

// Parses text pasted in the natural "numbered question" format:
//   1. Question text
//   a) option
//   b) option
//   c) option
//   d) option
//
//   2. Next question...
// Correct answer and explanation aren't in the paste — the teacher sets those
// per-question in the auto-filled cards below.
private fun parseNumberedMcqPaste(text: String): List<McqDraft> {
    val blocks = text.split(Regex("(?m)^(?=\\d+\\.\\s)")).map { it.trim() }.filter { it.isNotBlank() }
    return blocks.mapNotNull { block ->
        val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
        val qLine = lines.firstOrNull { Regex("^\\d+\\.").containsMatchIn(it) } ?: return@mapNotNull null
        val question = qLine.replaceFirst(Regex("^\\d+\\.\\s*"), "").trim()
        fun optionFor(letter: String) = lines
            .firstOrNull { it.startsWith("$letter)", ignoreCase = true) }
            ?.replaceFirst(Regex("^[a-dA-D]\\)\\s*"), "")?.trim() ?: ""
        if (question.isBlank()) return@mapNotNull null
        McqDraft(question, optionFor("a"), optionFor("b"), optionFor("c"), optionFor("d"))
    }
}

// Keeps only the filled options per draft and re-bases correctIndex against
// that filtered list, so a 2- or 3-option question still launches cleanly.
private fun mcqDraftsToItems(drafts: List<McqDraft>): List<McqItem> = drafts.mapNotNull { d ->
    val rawOptions = listOf(d.optionA, d.optionB, d.optionC, d.optionD)
    val letterIdx = "ABCD".indexOf(d.correctLetter).coerceAtLeast(0)
    val filledIndices = rawOptions.indices.filter { rawOptions[it].isNotBlank() }
    if (d.question.isBlank() || filledIndices.size < 2 || letterIdx !in filledIndices) return@mapNotNull null
    val options = filledIndices.map { rawOptions[it] }
    val correctIndex = filledIndices.indexOf(letterIdx)
    McqItem(d.question.trim(), options, correctIndex, d.explanation.trim())
}

@Composable
private fun BulkAddMcqDialog(onDismiss: () -> Unit, onQueueReady: (List<McqItem>) -> Unit) {
    var pasteText by remember { mutableStateOf("") }
    var drafts by remember { mutableStateOf<List<McqDraft>>(emptyList()) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(Color.White, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("Bulk Add MCQs", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                Text("एक बार बनाओ, class में सिर्फ Next दबाओ", fontSize = 13.sp, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(16.dp))
                Text("Paste All Questions At Once (optional) — Auto-Fill हो जाएगा नीचे", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pasteText, onValueChange = { pasteText = it },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    placeholder = { Text("1. Question text\na) option\nb) option\nc) option\nd) option\n\n2. Next question...") }
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFF12203D), RoundedCornerShape(12.dp))
                        .clickable { if (pasteText.isNotBlank()) drafts = parseNumberedMcqPaste(pasteText) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📥 Auto-Fill From Pasted Text", color = Color(0xFF12203D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(20.dp))

                drafts.forEachIndexed { idx, draft ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .background(Color(0xFFFBF8F1), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFEAE4D3), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Question ${idx + 1}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D), modifier = Modifier.weight(1f))
                            Text("✕", color = Color(0xFFC0392B), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { drafts = drafts.toMutableList().also { it.removeAt(idx) } })
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = draft.question,
                            onValueChange = { new -> drafts = drafts.toMutableList().also { it[idx] = it[idx].copy(question = new) } },
                            modifier = Modifier.fillMaxWidth(), placeholder = { Text("Question text") }
                        )
                        Spacer(Modifier.height(10.dp))
                        listOf(
                            Triple("Option A", draft.optionA) { new: String -> drafts = drafts.toMutableList().also { it[idx] = it[idx].copy(optionA = new) } },
                            Triple("Option B", draft.optionB) { new: String -> drafts = drafts.toMutableList().also { it[idx] = it[idx].copy(optionB = new) } },
                            Triple("Option C", draft.optionC) { new: String -> drafts = drafts.toMutableList().also { it[idx] = it[idx].copy(optionC = new) } },
                            Triple("Option D", draft.optionD) { new: String -> drafts = drafts.toMutableList().also { it[idx] = it[idx].copy(optionD = new) } }
                        ).forEach { (label, value, onChange) ->
                            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5F6B), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth())
                        }
                        Text("Correct Answer", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5F6B), modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("A", "B", "C", "D").forEach { letter ->
                                val selected = draft.correctLetter == letter
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (selected) Color(0xFF1F7A3D) else Color.White, RoundedCornerShape(10.dp))
                                        .border(1.dp, if (selected) Color(0xFF1F7A3D) else Color(0xFFEAE4D3), RoundedCornerShape(10.dp))
                                        .clickable { drafts = drafts.toMutableList().also { it[idx] = it[idx].copy(correctLetter = letter) } }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(letter, color = if (selected) Color.White else Color(0xFF12203D), fontWeight = FontWeight.Bold) }
                            }
                        }
                        Text("Explanation (optional)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5F6B), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        OutlinedTextField(
                            value = draft.explanation,
                            onValueChange = { new -> drafts = drafts.toMutableList().also { it[idx] = it[idx].copy(explanation = new) } },
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, LIVE_GOLD, RoundedCornerShape(12.dp))
                        .clickable { drafts = drafts + McqDraft() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("+ Add Question Manually", color = LIVE_GOLD, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(20.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { val items = mcqDraftsToItems(drafts); if (items.isNotEmpty()) onQueueReady(items) },
                    enabled = drafts.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = LIVE_GOLD),
                    modifier = Modifier.weight(1f)
                ) { Text("💾 Save & Launch First", color = Color(0xFF12203D), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// Calls your own backend (AI_GENERATE_MCQ_ENDPOINT), which should proxy the
// actual AI request and return JSON: {"question": "...", "options": ["...","..."], "correctIndex": 0, "explanation": "..."}
private suspend fun requestAiMcq(topic: String): McqItem = withContext(Dispatchers.IO) {
    val url = java.net.URL(AI_GENERATE_MCQ_ENDPOINT)
    val conn = url.openConnection() as java.net.HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        val body = org.json.JSONObject().put("topic", topic).toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(responseText)
        val question = json.getString("question")
        val optionsArray = json.getJSONArray("options")
        val options = (0 until optionsArray.length()).map { optionsArray.getString(it) }
        val correctIndex = json.optInt("correctIndex", 0)
        val explanation = json.optString("explanation", "")
        McqItem(question, options, correctIndex, explanation)
    } finally {
        conn.disconnect()
    }
}

@Composable
private fun AiGenerateMcqDialog(onDismiss: () -> Unit, onGenerated: (String, List<String>, Int, String) -> Unit) {
    var topic by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("🤖 AI Generate MCQ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = topic, onValueChange = { topic = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Topic — e.g. Wordsworth, Active-Passive Voice") }
            )
            if (error.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(error, color = Color(0xFFC0392B), fontSize = 11.5.sp) }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !loading) { Text("Cancel") }
                Button(
                    onClick = {
                        if (topic.isNotBlank()) {
                            loading = true; error = ""
                            scope.launch {
                                try {
                                    val item = requestAiMcq(topic.trim())
                                    onGenerated(item.question, item.options, item.correctIndex, item.explanation)
                                } catch (e: Exception) {
                                    error = "Could not generate MCQ: ${e.message}"
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    },
                    enabled = topic.isNotBlank() && !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB05C8C)),
                    modifier = Modifier.weight(1f)
                ) {
                    if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("✨ Generate", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TimerPickerDialog(onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    var customMinutes by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("⏱ Start Timer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(1, 2, 5, 10).forEach { min ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, LIVE_GOLD, RoundedCornerShape(10.dp))
                            .clickable { onStart(min * 60) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("${min}m", color = LIVE_GOLD, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Custom (minutes)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = customMinutes, onValueChange = { customMinutes = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("e.g. 15") }
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { customMinutes.toIntOrNull()?.let { if (it > 0) onStart(it * 60) } },
                    enabled = customMinutes.toIntOrNull()?.let { it > 0 } == true,
                    colors = ButtonDefaults.buttonColors(containerColor = LIVE_GOLD),
                    modifier = Modifier.weight(1f)
                ) { Text("Start", color = Color(0xFF12203D), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// My Script — NOT a Dialog. This renders inline, right in the same scrollable
// Column as the board/tool sections, so the board above stays visible and the
// whole page keeps scrolling normally instead of being blocked by an overlay.

// Compact version of the board's annotation toolbar, reused inside My Script
// so the teacher can mark up their own notes with the same pen/highlighter/
// eraser tools — same icons, same AnnotationTool enum, own separate strokes.
@Composable
private fun ScriptAnnotationToolRow(
    tool: AnnotationTool, onToolChange: (AnnotationTool) -> Unit,
    penColor: Color, onColorChange: (Color) -> Unit,
    penWidth: Float, onWidthChange: (Float) -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit, onClear: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                AnnotationTool.POINTER to "👆", AnnotationTool.MOVE to "✋", AnnotationTool.MARKER to "✏️",
                AnnotationTool.HIGHLIGHTER to "🖍️", AnnotationTool.ERASER to "🧹"
            ).forEach { (t, icon) ->
                val active = tool == t
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(if (active) LIVE_GOLD else Color.White, RoundedCornerShape(10.dp))
                        .border(1.dp, if (active) LIVE_GOLD else Color(0xFFEAE4D3), RoundedCornerShape(10.dp))
                        .clickable { onToolChange(t) },
                    contentAlignment = Alignment.Center
                ) { Text(icon, fontSize = 14.sp) }
            }
            Box(
                modifier = Modifier.size(38.dp).background(Color.White, RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFEAE4D3), RoundedCornerShape(10.dp)).clickable(onClick = onUndo),
                contentAlignment = Alignment.Center
            ) { Text("↩", fontSize = 14.sp, color = Color(0xFF12203D)) }
            Box(
                modifier = Modifier.size(38.dp).background(Color.White, RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFEAE4D3), RoundedCornerShape(10.dp)).clickable(onClick = onRedo),
                contentAlignment = Alignment.Center
            ) { Text("↪", fontSize = 14.sp, color = Color(0xFF12203D)) }
            Box(
                modifier = Modifier.size(38.dp).background(Color(0xFFFBE6E4), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFEAE4D3), RoundedCornerShape(10.dp)).clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) { Text("🧹", fontSize = 14.sp) }
            Spacer(Modifier.width(4.dp))
            TOOL_COLORS.forEach { c ->
                Box(
                    modifier = Modifier.size(24.dp).background(c, CircleShape)
                        .border(if (penColor == c) 2.dp else 0.dp, LIVE_GOLD, CircleShape)
                        .clickable { onColorChange(c) }
                )
            }
        }
    }
}

@Composable
private fun MyScriptPanel(
    editing: Boolean, savedScript: String, draft: String,
    onDraftChange: (String) -> Unit, onSave: () -> Unit, onEdit: () -> Unit, onClose: () -> Unit,
    // Annotation state — its own strokes, separate from the board's, so
    // marking up your script never touches what's drawn on the board.
    tool: AnnotationTool, onToolChange: (AnnotationTool) -> Unit,
    penColor: Color, onColorChange: (Color) -> Unit,
    penWidth: Float, onWidthChange: (Float) -> Unit,
    strokes: SnapshotStateList<InkShape>, redoStack: SnapshotStateList<InkShape>,
    modifier: Modifier = Modifier
) {
    // Fixed height, same idea as the teaching board's own 540dp box — so this
    // card never grows taller with the text. Long content scrolls INSIDE this
    // box (weight(1f) on the scrollable part below), not the whole page.
    // Rounded only on top (bottom-sheet style) now that the panel runs
    // full-bleed edge-to-edge, flush with the board's own square left/right
    // edges — fully rounded corners would look like they're sticking out.
    val scriptShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFBF8F1), scriptShape)
            .border(1.5.dp, LIVE_GOLD, scriptShape)
            .padding(14.dp)
    ) {
        if (editing) {
            Text(
                "Sirf aapko dikhega — kabhi students ke board pe nahi jaata. Yahan poem/notes paste/type karo, phir Save dabao.",
                fontSize = 13.sp, color = Color(0xFF5B5F6B), lineHeight = 19.sp
            )
            Spacer(Modifier.height(14.dp))
            // weight(1f) bounds this field's height to whatever's left in the
            // fixed-height card — Compose's TextField scrolls its own content
            // internally once it's height-constrained, so long pasted text
            // scrolls inside this box instead of pushing the card taller.
            OutlinedTextField(
                value = draft, onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("Your lesson script, talking points, reminders...") },
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LIVE_ORANGE, RoundedCornerShape(100.dp))
                    .clickable(onClick = onSave)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("✅ Save", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        } else {
            // Close pinned to the left corner, Edit to the right corner, tools
            // centered between them on the same row — a single compact header
            // line instead of stacked rows, leaving more room for the text below.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(LIVE_NAVY, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(8.dp))
                // Same annotation tools as the board — teacher can mark up their
                // own script (highlight, circle, underline) while teaching from it.
                // weight(1f) + horizontalScroll lets it take the center space
                // and still scroll sideways if it doesn't all fit.
                Box(modifier = Modifier.weight(1f)) {
                    ScriptAnnotationToolRow(
                        tool = tool, onToolChange = onToolChange,
                        penColor = penColor, onColorChange = onColorChange,
                        penWidth = penWidth, onWidthChange = onWidthChange,
                        onUndo = { if (strokes.isNotEmpty()) redoStack.add(strokes.removeAt(strokes.size - 1)) },
                        onRedo = { if (redoStack.isNotEmpty()) strokes.add(redoStack.removeAt(redoStack.size - 1)) },
                        onClear = { strokes.clear(); redoStack.clear() }
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(LIVE_NAVY, CircleShape)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) { Text("✎", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(6.dp))
            // Same idea: the saved text scrolls inside this fixed remaining
            // space rather than expanding the card past its 460dp height.
            // AnnotationCanvas sits on top so pen/highlighter/eraser strokes
            // land directly over the script text.
            Box(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(
                        savedScript.ifBlank { "No script saved yet." },
                        fontSize = 16.sp, color = Color(0xFF1A1A1A), lineHeight = 26.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Justify
                    )
                }
                AnnotationCanvas(
                    modifier = Modifier.fillMaxSize(),
                    tool = tool, color = penColor, penWidth = penWidth,
                    strokes = strokes, redoStack = redoStack,
                    swipeEnabled = false,
                    onSwipeLeft = {}, onSwipeRight = {},
                    onZoomPan = { _, _ -> }
                )
            }
        }
    }
}

// Teacher's chat panel — inline, same non-blocking pattern as My Script. The
// teacher always sees every message here regardless of the chatVisible /
// peerChat controls, which only filter what students see on their side.
@Composable
private fun ChatPanel(
    messages: List<ChatMessage>, draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFBF8F1), RoundedCornerShape(18.dp))
            .border(1.5.dp, LIVE_GOLD, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (messages.isEmpty()) {
                Text("No messages yet.", fontSize = 12.5.sp, color = Color(0xFF8A8F99))
            }
            messages.forEach { m ->
                Column(
                    horizontalAlignment = if (m.isTeacher) Alignment.End else Alignment.Start,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        if (m.isTeacher) "You" else m.senderName,
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        color = if (m.isTeacher) LIVE_GOLD else Color(0xFF5B5F6B)
                    )
                    Box(
                        modifier = Modifier
                            .background(if (m.isTeacher) LIVE_NAVY else Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, if (m.isTeacher) LIVE_NAVY else Color(0xFFEAE4D3), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(m.text, fontSize = 13.sp, color = if (m.isTeacher) Color.White else Color(0xFF1A1A1A))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draft, onValueChange = onDraftChange,
                modifier = Modifier.weight(1f), placeholder = { Text("Message the class...") }, singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(LIVE_GOLD, RoundedCornerShape(10.dp))
                    .clickable { if (draft.isNotBlank()) { onSend(); } }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) { Text("➤", color = Color(0xFF12203D), fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AdminLiveClassCard(teacherKey: String, teacherName: String = "Teacher", modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isLive by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    // Locked (default): the board stays pinned in place and only the tools
    // below it scroll. Unlocked: board and tools scroll together as one
    // page, like before.
    var boardLocked by remember { mutableStateOf(true) }

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
    var connectedStudents by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // name, mobile
    var showConnectedList by remember { mutableStateOf(false) }
    var pendingRequests by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) } // key, name, mobile

    // Classroom Controls — 5 toggles synced to Firebase, mirrored on the student side.
    var canUnmute by remember { mutableStateOf(true) }
    var canCamera by remember { mutableStateOf(true) }
    var chatVisible by remember { mutableStateOf(true) }
    var canSendMessages by remember { mutableStateOf(true) }
    var peerChat by remember { mutableStateOf(true) }
    var showClassroomControls by remember { mutableStateOf(false) }

    // Live Poll
    var pollActive by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var pollVotes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) } // studentKey -> optionIndex
    var showCreatePoll by remember { mutableStateOf(false) }

    // MCQ on Board
    var mcqActive by remember { mutableStateOf(false) }
    var mcqQuestion by remember { mutableStateOf("") }
    var mcqOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var mcqCorrectIndex by remember { mutableStateOf(-1) }
    var mcqExplanation by remember { mutableStateOf("") }
    var mcqVotes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var mcqRevealAnswer by remember { mutableStateOf(false) }
    var studentVotingEnabled by remember { mutableStateOf(true) }
    var showCreateMcq by remember { mutableStateOf(false) }
    var showBulkMcq by remember { mutableStateOf(false) }
    var showAiMcq by remember { mutableStateOf(false) }
    var mcqQueue by remember { mutableStateOf<List<McqItem>>(emptyList()) }
    var mcqQueueIndex by remember { mutableStateOf(0) }

    // Timer
    var timerRunning by remember { mutableStateOf(false) }
    var timerEndMillis by remember { mutableStateOf(0L) }
    var timerRemaining by remember { mutableStateOf(0) }
    var showTimerPicker by remember { mutableStateOf(false) }

    // My Script
    var showMyScript by remember { mutableStateOf(false) }
    var myScriptEditing by remember { mutableStateOf(true) }
    var myScript by remember { mutableStateOf("") }
    var myScriptDraft by remember { mutableStateOf("") }
    // Own annotation state for marking up the script — separate from the
    // board's pen/color/strokes so the two never interfere with each other.
    var myScriptTool by remember { mutableStateOf(AnnotationTool.POINTER) }
    var myScriptPenColor by remember { mutableStateOf(TOOL_COLORS[0]) }
    var myScriptPenWidth by remember { mutableStateOf(6f) }
    val myScriptStrokes = remember { mutableStateListOf<InkShape>() }
    val myScriptRedoStack = remember { mutableStateListOf<InkShape>() }

    // Classroom Chat
    var showChat by remember { mutableStateOf(false) }
    var chatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var chatDraft by remember { mutableStateOf("") }
    var chatUnread by remember { mutableStateOf(0) }

    var snapshotBusy by remember { mutableStateOf(false) }
    // Lets us programmatically scroll the board back into view before taking
    // a snapshot — without this, tapping Save Board Snapshot while scrolled
    // down to Tools captures whatever's on screen there instead of the board.
    val boardBringIntoViewRequester = remember { BringIntoViewRequester() }
    // Old-Compose-safe way to capture the board: track its on-screen position
    // (positionInWindow — a much older, always-available API than boundsInWindow),
    // pair it with the already-tracked boardSizePx, and crop a full-window
    // View.drawToBitmap() capture down to that rect.
    // (rememberGraphicsLayer()/drawLayer() need Compose UI 1.7+ — this doesn't.)
    var boardPositionInWindow by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val localView = LocalView.current
    val coroutineScope = rememberCoroutineScope()

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
                    val db = FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey")
                    db.child("slidePdf").setValue(b64)
                    db.child("currentPage").setValue(0)
                    db.child("boardMode").setValue(BoardMode.PDF.name)
                    boardMode = BoardMode.PDF
                    msg = "Slides shared with the class."
                } else msg = "Could not read that file."
            } catch (e: Exception) {
                msg = "Upload failed: ${e.message}"
            }
            uploading = false
        }
    }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/active")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) { isLive = s.getValue(Boolean::class.java) ?: false }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/slidePdf")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) { slidePdf = s.getValue(String::class.java) ?: "" }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/currentPage")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    currentPage = s.getValue(Int::class.java) ?: 0
                    strokes.clear(); redoStack.clear()
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/repeatRequests")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) { repeatCount = s.childrenCount.toInt() }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/joinRequests")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    pendingRequests = s.children.mapNotNull { c ->
                        val status = c.child("status").getValue(String::class.java) ?: "pending"
                        if (status != "pending") return@mapNotNull null
                        val name = c.child("name").getValue(String::class.java) ?: "Student"
                        val mobile = c.child("mobile").getValue(String::class.java) ?: ""
                        Triple(c.key ?: "", name, mobile)
                    }
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/participants")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    connectedStudents = s.children.map { c ->
                        val name = c.child("name").getValue(String::class.java) ?: "Student"
                        val mobile = c.child("mobile").getValue(String::class.java) ?: ""
                        name to mobile
                    }
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/controls")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    canUnmute = s.child("canUnmute").getValue(Boolean::class.java) ?: true
                    canCamera = s.child("canCamera").getValue(Boolean::class.java) ?: true
                    chatVisible = s.child("chatVisible").getValue(Boolean::class.java) ?: true
                    canSendMessages = s.child("canSendMessages").getValue(Boolean::class.java) ?: true
                    peerChat = s.child("peerChat").getValue(Boolean::class.java) ?: true
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/poll")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    pollActive = s.child("active").getValue(Boolean::class.java) ?: false
                    pollQuestion = s.child("question").getValue(String::class.java) ?: ""
                    pollOptions = s.child("options").children.mapNotNull { it.getValue(String::class.java) }
                    pollVotes = s.child("votes").children.associate { (it.key ?: "") to (it.getValue(Int::class.java) ?: 0) }
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/mcq")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    mcqActive = s.child("active").getValue(Boolean::class.java) ?: false
                    mcqQuestion = s.child("question").getValue(String::class.java) ?: ""
                    mcqOptions = s.child("options").children.mapNotNull { it.getValue(String::class.java) }
                    mcqCorrectIndex = s.child("correctIndex").getValue(Int::class.java) ?: -1
                    mcqRevealAnswer = s.child("revealAnswer").getValue(Boolean::class.java) ?: false
                    mcqExplanation = s.child("explanation").getValue(String::class.java) ?: ""
                    mcqVotes = s.child("votes").children.associate { (it.key ?: "") to (it.getValue(Int::class.java) ?: 0) }
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/studentVotingEnabled")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) { studentVotingEnabled = s.getValue(Boolean::class.java) ?: true }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/timer")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    timerRunning = s.child("running").getValue(Boolean::class.java) ?: false
                    timerEndMillis = s.child("endMillis").getValue(Long::class.java) ?: 0L
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/script")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) { myScript = s.getValue(String::class.java) ?: "" }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
        // Teacher always sees every message (moderation view) — chatVisible/peerChat
        // only filter what the STUDENT side renders, not what the teacher sees here.
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/chat").limitToLast(200)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(s: com.google.firebase.database.DataSnapshot) {
                    val incoming = s.children.mapNotNull { c ->
                        val text = c.child("text").getValue(String::class.java) ?: return@mapNotNull null
                        ChatMessage(
                            id = c.key ?: "",
                            senderId = c.child("senderId").getValue(String::class.java) ?: "",
                            senderName = c.child("senderName").getValue(String::class.java) ?: "Student",
                            text = text,
                            timestamp = c.child("timestamp").getValue(Long::class.java) ?: 0L,
                            isTeacher = c.child("isTeacher").getValue(Boolean::class.java) ?: false
                        )
                    }.sortedBy { it.timestamp }
                    if (!showChat && incoming.size > chatMessages.size) chatUnread += (incoming.size - chatMessages.size)
                    chatMessages = incoming
                }
                override fun onCancelled(e: com.google.firebase.database.DatabaseError) {}
            })
    }

    // Ticks the on-screen timer once a second while running; the source of truth
    // (endMillis) lives in Firebase so teacher and students always agree.
    LaunchedEffect(timerRunning, timerEndMillis) {
        while (timerRunning) {
            val remain = ((timerEndMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            timerRemaining = remain
            if (remain <= 0) {
                FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/timer/running").setValue(false)
                break
            }
            delay(1000)
        }
    }

    fun setClassroomControl(key: String, value: Boolean) {
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/controls/$key").setValue(value)
    }

    fun launchPoll(question: String, options: List<String>) {
        val db = FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/poll")
        db.child("question").setValue(question)
        db.child("options").setValue(options)
        db.child("votes").removeValue()
        db.child("active").setValue(true)
    }

    fun endPoll() {
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/poll/active").setValue(false)
    }

    fun launchMcq(question: String, options: List<String>, correctIndex: Int, explanation: String = "") {
        val db = FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/mcq")
        db.child("question").setValue(question)
        db.child("options").setValue(options)
        db.child("correctIndex").setValue(correctIndex)
        db.child("explanation").setValue(explanation)
        db.child("revealAnswer").setValue(false)
        db.child("votes").removeValue()
        db.child("active").setValue(true)
        boardMode = BoardMode.MCQ
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/boardMode").setValue(BoardMode.MCQ.name)
    }

    fun switchBoardBackToPdf() {
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/mcq/active").setValue(false)
        boardMode = BoardMode.PDF
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/boardMode").setValue(BoardMode.PDF.name)
    }

    fun toggleStudentVoting() {
        val next = !studentVotingEnabled
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/studentVotingEnabled").setValue(next)
    }

    fun muteAllStudents() {
        // One-shot signal — the student app listens for a change in this
        // timestamp and force-mutes locally, same pattern as slide sync.
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/muteAllSignal").setValue(System.currentTimeMillis())
        msg = "All students muted."
    }

    fun startTimer(durationSeconds: Int) {
        val db = FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/timer")
        db.child("endMillis").setValue(System.currentTimeMillis() + durationSeconds * 1000L)
        db.child("running").setValue(true)
    }

    fun stopTimer() {
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/timer/running").setValue(false)
    }

    // Matches the message format already working in the WebView app —
    // real GitHub Pages link with the join code as a query param, landing
    // straight on the live class section. teacherName here is the actual
    // parameter passed into this composable (not a hardcoded placeholder).
    fun shareClassLink() {
        FirebaseDatabase.getInstance().getReference("teachers").child(teacherKey).child("joinCode")
            .get()
            .addOnSuccessListener { snapshot ->
                val joinCode = snapshot.getValue(String::class.java)
                val text = if (!joinCode.isNullOrBlank()) {
                    "Hi Nation Builders! \uD83C\uDF93\n" +
                    "Your teacher, $teacherName, has invited you to join this Live Class.\n\n" +
                    "Get connected using the link below. Your regular attendance may lead you to the change you wish to see in your life.\n\n" +
                    "All the best \uD83D\uDC4D\n\n" +
                    "https://dadu2122.github.io/Shree-English-Classes/?jc=$joinCode#liveClassSection"
                } else {
                    "Join my live class on EngTeck! Open the app and enter your teacher's Join Code."
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(intent, "Share class link"))
            }
            .addOnFailureListener {
                msg = "Could not fetch join code — check your connection."
            }
    }

    fun saveBoardSnapshot() {
        snapshotBusy = true
        coroutineScope.launch {
            try {
                // If the board is currently scrolled out of view (teacher is
                // down in Tools), bring it back on screen first — drawToBitmap()
                // can only ever capture pixels that are ACTUALLY rendered on
                // screen right now, so skipping this silently captures whatever
                // section happens to be visible instead of the board.
                boardBringIntoViewRequester.bringIntoView()
                delay(120) // let the scroll settle and the board redraw
                val fullBitmap = localView.drawToBitmap()
                val left = boardPositionInWindow.x.toInt().coerceIn(0, fullBitmap.width - 1)
                val top = boardPositionInWindow.y.toInt().coerceIn(0, fullBitmap.height - 1)
                val width = boardSizePx.width.coerceIn(1, fullBitmap.width - left)
                val height = boardSizePx.height.coerceIn(1, fullBitmap.height - top)
                val bitmap = if (boardSizePx.width > 0 && boardSizePx.height > 0) {
                    Bitmap.createBitmap(fullBitmap, left, top, width, height)
                } else {
                    fullBitmap
                }
                val saved = saveBitmapToGallery(context, bitmap)
                msg = if (saved) "📸 Snapshot saved to Gallery." else "Snapshot failed — could not save to gallery."
            } catch (e: Exception) {
                msg = "Snapshot failed: ${e.message}"
            }
            snapshotBusy = false
        }
    }

    fun saveMyScript(text: String) {
        myScript = text
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/script").setValue(text)
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val ref = FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/chat").push()
        ref.setValue(
            mapOf(
                "senderId" to "teacher",
                "senderName" to teacherName,
                "text" to text.trim(),
                "timestamp" to System.currentTimeMillis(),
                "isTeacher" to true
            )
        )
    }

    fun goToQueuedMcq(index: Int) {
        if (index !in mcqQueue.indices) return
        mcqQueueIndex = index
        val item = mcqQueue[index]
        launchMcq(item.question, item.options, item.correctIndex, item.explanation)
    }

    LaunchedEffect(slidePdf, currentPage) {
        if (slidePdf.isNotBlank()) {
            try {
                val result = withContext(Dispatchers.Default) {
                    Pair(PdfSlideRenderer.pageCount(context, slidePdf), PdfSlideRenderer.renderPage(context, slidePdf, currentPage))
                }
                pageCount = result.first
                slideBitmap = result.second
            } catch (e: Exception) {
                android.util.Log.e("SDBoard", "PDF render failed", e)
                slideBitmap = null
                pageCount = 0
                msg = "Could not load the shared PDF — try sharing it again."
            }
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
            FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/active").setValue(true)
        }
        AgoraLiveAudio.onError = { err -> starting = false; msg = "Could not start: $err" }
        AgoraLiveAudio.join(context, "live_$teacherKey", 1)
    }

    fun stopClass() {
        AgoraLiveAudio.leave()
        connected = false; msg = ""
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/active").setValue(false)
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/participants").removeValue()
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/repeatRequests").removeValue()
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/joinRequests").removeValue()
    }

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/currentPage").setValue(clamped)
    }

    if (!connected) {
        Column(
            modifier = modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
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
    Column(modifier = modifier.fillMaxSize()) {

        // Dark status strip: S.D.BOARD | Connected: N | Lock | ON_AIR — monospace labels.
        Row(
            modifier = Modifier.fillMaxWidth().background(LIVE_NAVY).padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BlinkingDot(LIVE_GREEN, size = 6.dp)
                Spacer(Modifier.width(5.dp))
                Text("S.D.BOARD", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 1.2.sp)
                Spacer(Modifier.width(6.dp))
                Text("· $teacherName", color = LIVE_GOLD, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 34.dp)
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(100.dp))
                        .border(1.dp, LIVE_GOLD.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                        .clickable { showConnectedList = true }
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👥 ${connectedStudents.size}", color = LIVE_GOLD, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                Spacer(Modifier.width(6.dp))
                // Tap to pin/unpin the board. Locked = board stays fixed and only
                // the tools below scroll. Unlocked = board scrolls away with the
                // tools, like a normal page.
                Box(
                    modifier = Modifier
                        .background(if (boardLocked) LIVE_GOLD.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(100.dp))
                        .border(1.dp, if (boardLocked) LIVE_GOLD else Color.White.copy(alpha = 0.3f), RoundedCornerShape(100.dp))
                        .clickable { boardLocked = !boardLocked }
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(if (boardLocked) "🔒" else "🔓", fontSize = 9.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BlinkingDot(LIVE_GREEN, size = 6.dp)
                Spacer(Modifier.width(5.dp))
                Text("ON_AIR", color = LIVE_GREEN, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 0.8.sp)
            }
        }

        // Plain fillMaxWidth — no offset/measuring needed. This card's root
        // Column (above) has no padding of its own, and HomeScreen-equivalent
        // callers were confirmed to add none either, so this is naturally
        // edge-to-edge with zero guesswork.
        //
        // boardContent/belowBoardContent: split out so the board can be
        // placed either fixed above a scrolling tools panel (locked) or
        // inside the same scroll as everything else (unlocked) — see the
        // if (boardLocked) block further down.
        val boardContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp)
                .background(Color.White)
                .border(0.75.dp, Color.Black)
                .clip(androidx.compose.ui.graphics.RectangleShape)
                .bringIntoViewRequester(boardBringIntoViewRequester)
                .onSizeChanged { boardSizePx = it }
                // Tracks exactly where this box sits on screen so "Save Board
                // Snapshot" can crop the full-window capture down to just this
                // area (slide/text/whiteboard AND any poll/MCQ/timer overlay).
                .onGloballyPositioned { coordinates -> boardPositionInWindow = coordinates.positionInWindow() }
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
                when (boardMode) {
                    BoardMode.PDF -> {
                        if (slideBitmap != null) {
                            Image(bitmap = slideBitmap!!.asImageBitmap(), contentDescription = "Slide", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (slidePdf.isBlank()) "No PDF shared yet." else "Loading...", fontSize = 12.sp, color = Color(0xFF8A8F99))
                                }
                            }
                        }
                        BoardMode.PASTE_TEXT -> {
                            BoardPastedTextView(
                                pastedText = pastedText,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(20.dp)
                                    // Extra bottom padding while My Script is open —
                                    // otherwise the last lines end up scrolled UNDER
                                    // the floating panel and can never be brought
                                    // into view, since it visually covers them.
                                    .padding(bottom = if (showMyScript) 260.dp else 0.dp)
                            )
                        }
                        BoardMode.WHITEBOARD -> {
                            Box(Modifier.fillMaxSize())
                        }
                        BoardMode.MCQ -> {
                            McqBoardView(
                                queueIndex = mcqQueueIndex,
                                question = mcqQuestion, options = mcqOptions,
                                correctIndex = mcqCorrectIndex, explanation = mcqExplanation, revealAnswer = mcqRevealAnswer,
                                votes = mcqVotes,
                                onToggleReveal = {
                                    FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/mcq/revealAnswer").setValue(!mcqRevealAnswer)
                                },
                                queueSize = mcqQueue.size,
                                onPrevQueued = { if (mcqQueueIndex > 0) goToQueuedMcq(mcqQueueIndex - 1) },
                                onNextQueued = { if (mcqQueueIndex < mcqQueue.size - 1) goToQueuedMcq(mcqQueueIndex + 1) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (boardMode != BoardMode.PASTE_TEXT && boardMode != BoardMode.MCQ) {
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

            // Live Poll floats on top of whatever's underneath, like the WebView version.
            if (pollActive) {
                PollOverlayCard(
                    question = pollQuestion, options = pollOptions, votes = pollVotes,
                    onEnd = { endPoll() },
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.86f)
                )
            }

            // Countdown pill, top-right corner.
            if (timerRunning) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(LIVE_NAVY.copy(alpha = 0.92f), RoundedCornerShape(100.dp))
                        .border(1.dp, LIVE_GOLD, RoundedCornerShape(100.dp))
                        .clickable { stopTimer() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val mm = timerRemaining / 60
                    val ss = timerRemaining % 60
                    Text("⏱ %02d:%02d".format(mm, ss), color = LIVE_GOLD, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }

            // My Script floats on top of the board's upper half — same
            // picture-in-picture feel as the WebView version — instead of
            // living far down the page as an inline card.
            if (showMyScript) {
                MyScriptPanel(
                    editing = myScriptEditing,
                    savedScript = myScript,
                    draft = myScriptDraft,
                    onDraftChange = { myScriptDraft = it },
                    onSave = { saveMyScript(myScriptDraft); myScriptEditing = false },
                    onEdit = { myScriptDraft = myScript; myScriptEditing = true },
                    onClose = { showMyScript = false },
                    tool = myScriptTool, onToolChange = { myScriptTool = it },
                    penColor = myScriptPenColor, onColorChange = { myScriptPenColor = it },
                    penWidth = myScriptPenWidth, onWidthChange = { myScriptPenWidth = it },
                    strokes = myScriptStrokes, redoStack = myScriptRedoStack,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()  // edge-to-edge, same full bleed as the board itself
                        .height(270.dp)  // 50% of the board's 540dp height
                        .shadow(14.dp, RoundedCornerShape(0.dp))
                )
            }
        }
        }

        val belowBoardContent: @Composable () -> Unit = {
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

        Column(modifier = Modifier.fillMaxWidth().background(LIVE_NAVY).padding(horizontal = 16.dp, vertical = 16.dp)) {

            if (pendingRequests.isNotEmpty()) {
                Text("🔔 Join Requests", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = LIVE_GOLD, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                pendingRequests.forEach { (key, name, mobile) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            if (mobile.isNotEmpty()) Text(mobile, fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1F7A3D), RoundedCornerShape(10.dp))
                                .clickable {
                                    FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/joinRequests")
                                        .child(key).child("status").setValue("approved")
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("Approve", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFC0392B), RoundedCornerShape(10.dp))
                                .clickable {
                                    FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/joinRequests")
                                        .child(key).child("status").setValue("rejected")
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("Reject", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (repeatCount > 0) {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFCF3D9), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("🔁 $repeatCount student(s) asked you to repeat", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF946B00))
                }
                Spacer(Modifier.height(14.dp))
            }

            // Mode switch: PDF / Paste Text / Whiteboard — ONLY switches, does nothing else.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(Triple(BoardMode.PDF, "📄", "PDF"), Triple(BoardMode.PASTE_TEXT, "📜", "Paste Text"), Triple(BoardMode.WHITEBOARD, "✏️", "Whiteboard")).forEach { (mode, icon, label) ->
                    Box(
                        modifier = Modifier.weight(1f)
                            .background(LIVE_NAVY, RoundedCornerShape(100.dp))
                            .border(if (boardMode == mode) 1.5.dp else 0.dp, LIVE_GOLD, RoundedCornerShape(100.dp))
                            .clickable {
                                boardMode = mode
                                if (mode == BoardMode.PASTE_TEXT && pastedText.isBlank()) pasteEditing = true
                                FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey/boardMode").setValue(mode.name)
                            }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$icon $label", color = if (boardMode == mode) LIVE_GOLD else Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Tool row — LazyRow keeps ALL tools in one horizontally-scrolling
            // line; nothing ever wraps to a second row, no matter how many tools.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(listOf(
                    AnnotationTool.POINTER to "👆", AnnotationTool.MOVE to "✋", AnnotationTool.MARKER to "✏️", AnnotationTool.HIGHLIGHTER to "🖍️",
                    AnnotationTool.ERASER to "🧹", AnnotationTool.RECTANGLE to "▭", AnnotationTool.CIRCLE to "○",
                    AnnotationTool.LINE to "➖", AnnotationTool.ARROW to "➡️"
                )) { (t, icon) ->
                    val active = tool == t
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(if (active) LIVE_GOLD else LIVE_NAVY, RoundedCornerShape(9.dp))
                            .border(1.dp, if (active) LIVE_GOLD else Color.White.copy(alpha = 0.08f), RoundedCornerShape(9.dp))
                            .clickable { tool = t },
                        contentAlignment = Alignment.Center
                    ) { Text(icon, fontSize = 12.sp) }
                }
                item {
                    Box(
                        modifier = Modifier.size(30.dp).background(LIVE_NAVY, RoundedCornerShape(9.dp))
                            .clickable { if (strokes.isNotEmpty()) { redoStack.add(strokes.removeAt(strokes.size - 1)) } },
                        contentAlignment = Alignment.Center
                    ) { Text("↩", fontSize = 11.sp, color = Color.White) }
                }
                item {
                    Box(
                        modifier = Modifier.size(30.dp).background(LIVE_NAVY, RoundedCornerShape(9.dp))
                            .clickable { if (redoStack.isNotEmpty()) { strokes.add(redoStack.removeAt(redoStack.size - 1)) } },
                        contentAlignment = Alignment.Center
                    ) { Text("↪", fontSize = 11.sp, color = Color.White) }
                }
                item {
                    Box(
                        modifier = Modifier.size(30.dp).background(Color(0xFF3A1A1A), RoundedCornerShape(9.dp))
                            .clickable { strokes.clear(); redoStack.clear() },
                        contentAlignment = Alignment.Center
                    ) { Text("🧹", fontSize = 11.sp) }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf("Thin" to 3f, "Medium" to 6f, "Thick" to 12f).forEach { (label, w) ->
                    Box(
                        modifier = Modifier.background(if (penWidth == w) LIVE_GOLD else LIVE_NAVY, RoundedCornerShape(100.dp))
                            .clickable { penWidth = w }.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (penWidth == w) Color(0xFF12203D) else Color(0xFF8A8F99)) }
                }
                Spacer(Modifier.width(3.dp))
                Text("COLOUR:", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A8F99), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                TOOL_COLORS.forEach { c ->
                    Box(
                        modifier = Modifier.size(18.dp).background(c, CircleShape)
                            .border(if (penColor == c) 2.dp else 0.dp, LIVE_GOLD, CircleShape)
                            .clickable { penColor = c }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { muted = !muted; AgoraLiveAudio.setMuted(muted) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (muted) Color(0xFF8A8F99) else Color(0xFF1B6B79)),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) { Text(if (muted) "🔇" else "🎤", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1) }
                Button(
                    onClick = { stopClass() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) { Text("⏹", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1) }
                Button(
                    onClick = { pdfPickerLauncher.launch("application/pdf") }, enabled = !uploading,
                    colors = ButtonDefaults.buttonColors(containerColor = LIVE_GOLD), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp), contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    if (uploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Text("📂 PDF", color = Color(0xFF12203D), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1)
                }
                Button(
                    onClick = { if (!pasteEditing) { pastedTextDraft = pastedText }; pasteEditing = !pasteEditing },
                    colors = ButtonDefaults.buttonColors(containerColor = if (pasteEditing) LIVE_GOLD else Color(0xFFF5F3EC)),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).height(44.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) { Text("📜 TEXT", color = Color(0xFF12203D), fontWeight = FontWeight.Bold, fontSize = 10.5.sp, maxLines = 1) }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LIVE_NAVY, RoundedCornerShape(12.dp))
                    .border(1.dp, LIVE_GOLD.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { showClassroomControls = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛡", fontSize = 15.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "CLASSROOM CONTROL", color = LIVE_GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = LIVE_GOLD, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            // ---------- MCQ ON BOARD ----------
            Spacer(Modifier.height(16.dp))
            SectionPill("MCQ ON BOARD ▾", LIVE_GOLD, Color(0xFF12203D))
            Spacer(Modifier.height(8.dp))
            SectionRow("📝  Create One MCQ", LIVE_GOLD) { showCreateMcq = true }
            Spacer(Modifier.height(8.dp))
            SectionRow("📥  Bulk Add MCQs (No Retyping)", LIVE_GOLD) { showBulkMcq = true }
            Spacer(Modifier.height(8.dp))
            SectionRow("🤖  AI Generate MCQ", Color(0xFFB05C8C)) { showAiMcq = true }
            Spacer(Modifier.height(8.dp))
            SectionRow(if (studentVotingEnabled) "✋  Student Voting: ON" else "✋  Student Voting: OFF", Color(0xFF1B6B79)) { toggleStudentVoting() }
            Spacer(Modifier.height(8.dp))
            SectionRow("📄  Switch back to PDF", Color(0xFF5B5F6B)) { switchBoardBackToPdf() }

            // ---------- LIVE POLL ON BOARD ----------
            Spacer(Modifier.height(16.dp))
            SectionPill("LIVE POLL ON BOARD", LIVE_ORANGE, Color.White)
            Spacer(Modifier.height(8.dp))
            if (pollActive) {
                SectionRow("⏹  End Live Poll", LIVE_ORANGE) { endPoll() }
            } else {
                SectionRow("📊  Launch Live Poll", LIVE_ORANGE) { showCreatePoll = true }
            }

            // ---------- TOOLS ----------
            Spacer(Modifier.height(16.dp))
            SectionPill("TOOLS ▾", LIVE_MAROON, Color.White)
            Spacer(Modifier.height(8.dp))
            if (timerRunning) {
                SectionRow("⏱  Stop Timer", LIVE_GOLD) { stopTimer() }
            } else {
                SectionRow("⏱  Start Timer", LIVE_GOLD) { showTimerPicker = true }
            }
            Spacer(Modifier.height(8.dp))
            SectionRow("🔇  Mute All Students", Color(0xFFC0392B)) { muteAllStudents() }
            Spacer(Modifier.height(8.dp))
            SectionRow(if (snapshotBusy) "📸  Saving..." else "📸  Save Board Snapshot", Color(0xFF1B6B79)) { if (!snapshotBusy) saveBoardSnapshot() }
            Spacer(Modifier.height(8.dp))
            SectionRow("🔗  Share Class Link", Color(0xFF1B6B79)) { shareClassLink() }
            Spacer(Modifier.height(8.dp))
            SectionRow(
                if (showChat) "💬  Classroom Chat (open)" else if (chatUnread > 0) "💬  Classroom Chat ($chatUnread new)" else "💬  Classroom Chat",
                Color(0xFF1B6B79)
            ) {
                showChat = !showChat
                if (showChat) chatUnread = 0
            }
            if (showChat) {
                Spacer(Modifier.height(10.dp))
                ChatPanel(
                    messages = chatMessages, draft = chatDraft,
                    onDraftChange = { chatDraft = it },
                    onSend = { sendChatMessage(chatDraft); chatDraft = "" }
                )
            }
            Spacer(Modifier.height(8.dp))
            SectionRow(if (showMyScript) "📖  My Script (open)" else "📖  My Script", Color(0xFFB05C8C)) {
                if (showMyScript) {
                    showMyScript = false
                } else {
                    myScriptDraft = myScript
                    myScriptEditing = myScript.isBlank()
                    showMyScript = true
                }
            }
            // My Script ab board ke upar floating overlay ki tarah dikhta hai
            // (upar board Box ke andar) — WebView jaisa — yahan inline nahi.

            if (pasteEditing) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pastedTextDraft, onValueChange = { pastedTextDraft = it },
                    modifier = Modifier.fillMaxWidth().height(180.dp), // doubled — more room to see the pasted text
                    placeholder = { Text("Paste text to show on board...", color = Color(0xFF8A8F99)) },
                    // Explicit colors — without these it was inheriting the app's
                    // default (dark) text color, which is invisible against this
                    // dark navy TOOLS background.
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF12203D),
                        unfocusedContainerColor = Color(0xFF12203D),
                        focusedBorderColor = LIVE_GOLD,
                        unfocusedBorderColor = LIVE_GOLD.copy(alpha = 0.5f),
                        cursorColor = LIVE_GOLD
                    )
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        pastedText = pastedTextDraft
                        pasteEditing = false // collapse after saving
                        boardMode = BoardMode.PASTE_TEXT // auto-show it on the board
                        val db = FirebaseDatabase.getInstance().getReference("liveClasses/$teacherKey")
                        db.child("pastedText").setValue(pastedTextDraft)
                        db.child("boardMode").setValue(BoardMode.PASTE_TEXT.name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LIVE_GOLD),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) { Text("💾 Save to Board", color = Color(0xFF12203D), fontWeight = FontWeight.Bold) }
            }

            if (msg.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Text(msg, fontSize = 12.sp, color = LIVE_GOLD, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        }

        if (boardLocked) {
            // Board stays put; only the strip + tools below it scroll, in the
            // remaining space (weight(1f) makes that space exact, never more).
            boardContent()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                belowBoardContent()
                Spacer(Modifier.height(90.dp))
            }
        } else {
            // Unlocked — board and tools scroll together as one normal page.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                boardContent()
                belowBoardContent()
                Spacer(Modifier.height(90.dp))
            }
        }
    }

    if (showConnectedList) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showConnectedList = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text("👥 Connected Students (${connectedStudents.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                Spacer(Modifier.height(14.dp))
                if (connectedStudents.isEmpty()) {
                    Text("Abhi koi student connected nahi hai.", fontSize = 12.5.sp, color = Color(0xFF5B5F6B))
                } else {
                    Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        connectedStudents.forEachIndexed { index, (name, mobile) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}.", fontSize = 12.sp, color = Color(0xFF8A8F99), modifier = Modifier.width(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                                    if (mobile.isNotEmpty()) Text(mobile, fontSize = 10.5.sp, color = Color(0xFF5B5F6B))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = { showConnectedList = false }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }

    if (showClassroomControls) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showClassroomControls = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFBF8F1), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(34.dp).background(Color(0xFFEFE6CC), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("🛡", fontSize = 16.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text("Classroom Controls", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                }
                Spacer(Modifier.height(18.dp))

                Text("AUDIO / VIDEO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A8F99), letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                ClassroomControlToggle("Students can unmute", "Raise hand & speak permission", canUnmute) {
                    canUnmute = it; setClassroomControl("canUnmute", it)
                }
                Spacer(Modifier.height(10.dp))
                ClassroomControlToggle("Students can turn on camera", null, canCamera) {
                    canCamera = it; setClassroomControl("canCamera", it)
                }

                Spacer(Modifier.height(18.dp))
                Text("CHAT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A8F99), letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                ClassroomControlToggle("Classroom chat visible", "Hide or show the whole chat panel", chatVisible) {
                    chatVisible = it; setClassroomControl("chatVisible", it)
                }
                Spacer(Modifier.height(10.dp))
                ClassroomControlToggle("Students can send messages", "Read-only vs typing allowed", canSendMessages) {
                    canSendMessages = it; setClassroomControl("canSendMessages", it)
                }
                Spacer(Modifier.height(10.dp))
                ClassroomControlToggle("Student ↔ Student chat", "Peer chat vs only-teacher messaging", peerChat) {
                    peerChat = it; setClassroomControl("peerChat", it)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { showClassroomControls = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12203D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("Close", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showCreatePoll) {
        CreatePollDialog(
            onDismiss = { showCreatePoll = false },
            onLaunch = { q, opts -> launchPoll(q, opts); showCreatePoll = false }
        )
    }

    if (showCreateMcq) {
        CreateMcqDialog(
            onDismiss = { showCreateMcq = false },
            onLaunch = { q, opts, correct, expl -> launchMcq(q, opts, correct, expl); showCreateMcq = false }
        )
    }

    if (showBulkMcq) {
        BulkAddMcqDialog(
            onDismiss = { showBulkMcq = false },
            onQueueReady = { parsed ->
                mcqQueue = parsed
                mcqQueueIndex = 0
                if (parsed.isNotEmpty()) {
                    val item = parsed[0]
                    launchMcq(item.question, item.options, item.correctIndex, item.explanation)
                }
                showBulkMcq = false
            }
        )
    }

    if (showAiMcq) {
        AiGenerateMcqDialog(
            onDismiss = { showAiMcq = false },
            onGenerated = { q, opts, correct, expl -> launchMcq(q, opts, correct, expl); showAiMcq = false }
        )
    }

    if (showTimerPicker) {
        TimerPickerDialog(
            onDismiss = { showTimerPicker = false },
            onStart = { seconds -> startTimer(seconds); showTimerPicker = false }
        )
    }

    // My Script is rendered inline above (next to its SectionRow), not as a Dialog —
    // see the "📖 My Script" row in the TOOLS section.

    // Bulk-add MCQ queue: after teacher moves on, "Next MCQ" is exposed via
    // the queue index — advance it from the MCQ board view's own controls
    // if you wire a Next button there; kept minimal here by design.
}

@Composable
private fun ClassroomControlToggle(label: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFEAE4D3), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            if (subtitle != null) Text(subtitle, fontSize = 10.5.sp, color = Color(0xFF8A8F99))
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LIVE_GOLD,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCFCABB)
            )
        )
    }
}

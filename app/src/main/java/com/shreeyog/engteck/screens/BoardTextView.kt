package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BoardTextBlock(val text: String, val isPoem: Boolean)

// Splits long pasted text into "pages" (like the PDF viewer's 1/2, 2/2...),
// breaking at paragraph boundaries near the character limit so a page never
// cuts a sentence in half where avoidable.
fun paginateBoardText(raw: String, charsPerPage: Int = 1400): List<String> {
    if (raw.isBlank()) return listOf("")
    val blocks = raw.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
    val pages = mutableListOf<String>()
    var current = StringBuilder()
    for (block in blocks) {
        if (current.isNotEmpty() && current.length + block.length > charsPerPage) {
            pages.add(current.toString().trim())
            current = StringBuilder()
        }
        if (current.isNotEmpty()) current.append("\n\n")
        current.append(block)
    }
    if (current.isNotEmpty()) pages.add(current.toString().trim())
    return if (pages.isEmpty()) listOf("") else pages
}

// Splits pasted text on blank lines into blocks, then guesses whether each
// block is a poem stanza (short lines — keep exact line breaks, no justify)
// or a prose paragraph (join into one flowing block, justify it).
fun formatPastedBoardText(raw: String): List<BoardTextBlock> {
    if (raw.isBlank()) return emptyList()
    val blocks = raw.split(Regex("\\n\\s*\\n"))
    return blocks.mapNotNull { block ->
        val trimmed = block.trim('\n', ' ')
        if (trimmed.isBlank()) return@mapNotNull null
        val lines = trimmed.split("\n").filter { it.isNotBlank() }
        // Line-length alone isn't reliable: real poems (blank verse, iambic
        // pentameter etc.) often run 40-60 characters a line — as long as
        // ordinary wrapped prose. What actually distinguishes them is
        // VARIATION: a real poem's line breaks are deliberate, so line
        // lengths swing noticeably (a 15-char line next to a 55-char one).
        // Prose that got line-wrapped by a copy source wraps at a near-fixed
        // width, so its lines cluster tightly around the same length.
        val lens = lines.map { it.length }
        val avgLen = if (lens.isNotEmpty()) lens.average() else 0.0
        val stdDev = if (lens.isNotEmpty()) {
            kotlin.math.sqrt(lens.sumOf { (it - avgLen) * (it - avgLen) } / lens.size)
        } else 0.0
        val isPoem = lines.size >= 2 && (avgLen < 40 || stdDev > avgLen * 0.18)
        val displayText = if (isPoem) trimmed else lines.joinToString(" ") { it.trim() }
        BoardTextBlock(displayText, isPoem)
    }
}

private suspend fun fetchWordMeaning(word: String): String = withContext(Dispatchers.IO) {
    try {
        val clean = word.lowercase().filter { it.isLetter() }
        if (clean.isBlank()) return@withContext "No meaning found."
        val url = java.net.URL("https://api.dictionaryapi.dev/api/v2/entries/en/$clean")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        if (conn.responseCode != 200) return@withContext "No meaning found for \"$word\"."
        val body = conn.inputStream.bufferedReader().readText()
        val arr = org.json.JSONArray(body)
        val meanings = arr.getJSONObject(0).getJSONArray("meanings")
        val first = meanings.getJSONObject(0)
        val partOfSpeech = first.optString("partOfSpeech", "")
        val def = first.getJSONArray("definitions").getJSONObject(0).optString("definition", "No meaning found.")
        if (partOfSpeech.isNotBlank()) "($partOfSpeech) $def" else def
    } catch (e: Exception) {
        "Could not fetch meaning — check internet connection."
    }
}

private fun wordAtOffset(text: String, offset: Int): String {
    if (text.isEmpty()) return ""
    val safeOffset = offset.coerceIn(0, text.length - 1)
    if (!text[safeOffset].isLetterOrDigit()) return ""
    var start = safeOffset
    var end = safeOffset
    while (start > 0 && text[start - 1].isLetterOrDigit()) start--
    while (end < text.length - 1 && text[end + 1].isLetterOrDigit()) end++
    return text.substring(start, end + 1)
}

// Drop-in replacement for the plain "Paste Text" board display. Renders
// justified prose / preserved poem stanzas (using the same proven Text +
// LineBreak.Paragraph approach as the app's existing JustifiedText), and
// lets students tap any word to see its meaning in a small popup.
@Composable
fun BoardPastedTextView(pastedText: String, modifier: Modifier = Modifier, textColor: Color = Color(0xFF1A1A1A)) {
    val scope = rememberCoroutineScope()
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var definition by remember { mutableStateOf("") }
    var loadingDef by remember { mutableStateOf(false) }

    // Defensive: if the poem/prose detection logic ever throws on some edge
    // case input, fall back to showing the raw pasted text as one plain block
    // instead of crashing the whole board screen.
    val blocks = remember(pastedText) {
        try {
            formatPastedBoardText(pastedText)
        } catch (e: Exception) {
            if (pastedText.isBlank()) emptyList() else listOf(BoardTextBlock(pastedText, isPoem = true))
        }
    }

    fun onWordTapped(word: String) {
        val cleaned = word.trim(',', '.', ';', ':', '"', '\'', '!', '?', '(', ')', '—', '-')
        if (cleaned.isBlank()) return
        selectedWord = cleaned
        loadingDef = true
        definition = ""
        scope.launch {
            definition = fetchWordMeaning(cleaned)
            loadingDef = false
        }
    }

    // verticalScroll is what was missing — without it, text taller than the
    // board's fixed-height frame simply gets clipped top/bottom with no way
    // to reach the rest. This makes the block scrollable in place.
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        if (blocks.isEmpty()) {
            Text("Paste text below to show it here.", fontSize = 15.sp, color = textColor)
        } else {
            blocks.forEach { block ->
                var layoutResult by remember(block.text) { mutableStateOf<TextLayoutResult?>(null) }
                if (block.isPoem) {
                    Text(
                        text = block.text,
                        style = TextStyle(fontSize = 15.sp, color = textColor, lineHeight = 24.sp, textAlign = TextAlign.Start),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(block.text) {
                                detectTapGestures { tapPos ->
                                    layoutResult?.let { layout ->
                                        val charOffset = layout.getOffsetForPosition(tapPos)
                                        val word = wordAtOffset(block.text, charOffset)
                                        if (word.isNotBlank()) onWordTapped(word)
                                    }
                                }
                            },
                        onTextLayout = { layoutResult = it }
                    )
                } else {
                    JustifiedText(
                        text = block.text,
                        style = TextStyle(fontSize = 15.sp, color = textColor, lineHeight = 24.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(block.text) {
                                detectTapGestures { tapPos ->
                                    layoutResult?.let { layout ->
                                        val charOffset = layout.getOffsetForPosition(tapPos)
                                        val word = wordAtOffset(block.text, charOffset)
                                        if (word.isNotBlank()) onWordTapped(word)
                                    }
                                }
                            },
                        onTextLayout = { layoutResult = it }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (selectedWord != null) {
        Dialog(onDismissRequest = { selectedWord = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text(selectedWord ?: "", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                Spacer(Modifier.height(10.dp))
                if (loadingDef) {
                    CircularProgressIndicator(color = Color(0xFF12203D), modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(definition, fontSize = 13.5.sp, color = Color(0xFF1A1A1A), lineHeight = 19.sp)
                }
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = { selectedWord = null }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

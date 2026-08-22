package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BoardTextBlock(val text: String, val isPoem: Boolean)

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
        val avgLen = if (lines.isNotEmpty()) lines.sumOf { it.length } / lines.size else 0
        // Genuine poem lines are usually quite short (deliberate breaks); prose
        // pasted from a document/app also wraps into separate lines but those
        // tend to run much longer per line — so only treat it as a poem when
        // lines are consistently short AND there are several of them.
        val isPoem = lines.size >= 3 && avgLen < 32
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

// Drop-in replacement for the plain "Paste Text" board display. Renders
// justified prose / preserved poem stanzas, and lets students tap any word
// to see its meaning in a small popup.
@Composable
fun BoardPastedTextView(pastedText: String, modifier: Modifier = Modifier, textColor: Color = Color(0xFF1A1A1A)) {
    val scope = rememberCoroutineScope()
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var definition by remember { mutableStateOf("") }
    var loadingDef by remember { mutableStateOf(false) }

    val blocks = remember(pastedText) { formatPastedBoardText(pastedText) }

    Column(modifier = modifier) {
        if (blocks.isEmpty()) {
            Text("Paste text below to show it here.", fontSize = 15.sp, color = textColor)
        } else {
            blocks.forEach { block ->
                val annotated = remember(block.text) {
                    buildAnnotatedString {
                        var i = 0
                        val regex = Regex("\\S+")
                        for (match in regex.findAll(block.text)) {
                            if (match.range.first > i) append(block.text.substring(i, match.range.first))
                            pushStringAnnotation(tag = "WORD", annotation = match.value)
                            append(match.value)
                            pop()
                            i = match.range.last + 1
                        }
                        if (i < block.text.length) append(block.text.substring(i))
                    }
                }
                ClickableText(
                    text = annotated,
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 24.sp,
                        textAlign = if (block.isPoem) TextAlign.Start else TextAlign.Justify
                    ),
                    onClick = { offset ->
                        annotated.getStringAnnotations(tag = "WORD", start = offset, end = offset).firstOrNull()?.let { ann ->
                            val word = ann.item.trim(',', '.', ';', ':', '"', '\'', '!', '?', '(', ')', '—', '-')
                            if (word.isNotBlank()) {
                                selectedWord = word
                                loadingDef = true
                                definition = ""
                                scope.launch {
                                    definition = fetchWordMeaning(word)
                                    loadingDef = false
                                }
                            }
                        }
                    }
                )
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

package com.shreeyog.engteck.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay

// ============================================================================
// Data model + parsing
// ============================================================================
data class Flashcard(val number: String, val word: String, val partOfSpeech: String, val meaning: String, val example: String)
data class FlashcardDeck(val key: String, val label: String, val count: Int)

private val DECK_LABELS = mapOf(
    "vocabulary" to "Vocabulary",
    "idioms" to "Idioms & Phrases",
    "literaryTerms" to "Literary Terms",
    "grammarRules" to "Grammar Rules"
)

// Expected raw text format (one entry per numbered block):
//
// 1. Ubiquitous (adjective)
// Meaning: Present everywhere at the same time.
// Example: Smartphones have become ubiquitous in modern life.
fun parseFlashcards(raw: String): List<Flashcard> {
    if (raw.isBlank()) return emptyList()
    val parts = raw.split(Regex("\n(?=\\s*\\d+[.)]\\s)"))
    return parts.map { it.trim() }.filter { it.isNotEmpty() }.mapIndexedNotNull { i, block ->
        val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return@mapIndexedNotNull null
        val headerLine = lines[0].replace(Regex("^\\d+[.)]\\s*"), "")
        val posMatch = Regex("^(.*?)\\s*\\(([^)]+)\\)\\s*$").find(headerLine)
        val word = posMatch?.groupValues?.get(1)?.trim() ?: headerLine
        val pos = posMatch?.groupValues?.get(2)?.trim() ?: ""
        var meaning = ""
        var example = ""
        lines.drop(1).forEach { line ->
            when {
                line.startsWith("Meaning:", ignoreCase = true) -> meaning = line.substringAfter(":").trim()
                line.startsWith("Example:", ignoreCase = true) -> example = line.substringAfter(":").trim()
            }
        }
        if (word.isBlank() || meaning.isBlank()) null
        else Flashcard((i + 1).toString(), word, pos, meaning, example)
    }
}

// ============================================================================
// Home-screen section — heading + horizontal swipeable deck cards.
// Free & open: no login/premium check, reads a public Firebase path.
// ============================================================================
@Composable
fun FlashcardsCard(onOpenDeck: (deckKey: String, deckLabel: String) -> Unit, onManageContent: () -> Unit) {
    var decks by remember { mutableStateOf<List<FlashcardDeck>>(emptyList()) }
    var tapCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("flashcardsPublic")
            .get().addOnSuccessListener { snapshot ->
                decks = DECK_LABELS.keys.mapNotNull { key ->
                    val raw = snapshot.child(key).child("raw").getValue(String::class.java) ?: ""
                    val count = parseFlashcards(raw).size
                    if (count > 0) FlashcardDeck(key, DECK_LABELS[key] ?: key, count) else null
                }
            }
    }

    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(1500)
            tapCount = 0
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Glossy flat rectangular heading card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF17284A), Color(0xFF0E1930))),
                    RoundedCornerShape(18.dp)
                )
                .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Grow with Flashcards",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Read, Repeat, Remember",
                color = Color(0xFFF0D384),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text(
                "＋ Manage Content",
                color = Color(0xFF8A8E9E),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    tapCount++
                    if (tapCount >= 4) {
                        tapCount = 0
                        onManageContent()
                    }
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        if (decks.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Text("Flashcard decks coming soon.", color = Color(0xFF8A8E9E), fontSize = 12.sp)
            }
        } else if (decks.size == 1) {
            // Single deck: center it instead of left-anchoring in a scroll row
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                FlashcardDeckCard(decks[0], onClick = { onOpenDeck(decks[0].key, decks[0].label) })
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 22.dp, end = 44.dp)
            ) {
                items(decks) { deck ->
                    FlashcardDeckCard(deck, onClick = { onOpenDeck(deck.key, deck.label) })
                }
            }
        }
    }
}

@Composable
private fun FlashcardDeckCard(deck: FlashcardDeck, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF12203D), Color(0xFF1B2E52)))
            )
            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🃏", fontSize = 26.sp)
        Spacer(Modifier.height(10.dp))
        Text(deck.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("${deck.count} cards", color = Color(0xFFF0D384), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

// ============================================================================
// Full flashcard screen — tap to flip, Known / Review Again
// ============================================================================
@Composable
fun FlashcardScreen(deckKey: String, deckLabel: String, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var cards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var idx by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var knownCount by remember { mutableStateOf(0) }
    var reviewCount by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(deckKey) {
        FirebaseDatabase.getInstance().getReference("flashcardsPublic").child(deckKey).child("raw")
            .get().addOnSuccessListener {
                loading = false
                cards = parseFlashcards(it.getValue(String::class.java) ?: "")
            }.addOnFailureListener { loading = false }
    }

    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "flip"
    )

    fun goNext(known: Boolean) {
        if (known) knownCount++ else reviewCount++
        if (idx + 1 >= cards.size) {
            finished = true
        } else {
            idx++
            flipped = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFAF8F3))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12203D))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Back", color = Color.White) }
            Spacer(Modifier.width(4.dp))
            Text(deckLabel, color = Color(0xFFD4A017), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF12203D))
            }
            cards.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Flashcards abhi ready nahi hain.", color = Color(0xFF5B5F6B), fontSize = 14.sp)
            }
            finished -> FlashcardResultView(
                known = knownCount,
                review = reviewCount,
                total = cards.size,
                onRestart = {
                    idx = 0; flipped = false; knownCount = 0; reviewCount = 0; finished = false
                },
                onBack = onBack
            )
            else -> {
                val card = cards[idx]
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    val progress = (idx + 1f) / cards.size
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .background(Color(0xFFE3DFD3), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(progress).height(6.dp)
                                .background(Color(0xFFD4A017), RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(deckLabel, color = Color(0xFF8A8E9E), fontSize = 12.sp)
                        Text("${idx + 1} / ${cards.size}", color = Color(0xFF8A8E9E), fontSize = 12.sp)
                    }

                    Spacer(Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .align(Alignment.CenterHorizontally)
                            .graphicsLayer {
                                rotationY = rotation
                                cameraDistance = 12f * density
                            }
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (rotation <= 90f) Color(0xFF12203D) else Color(0xFFFAF8F3))
                            .border(3.dp, Color(0xFFD4A017), RoundedCornerShape(22.dp))
                            .clickable { flipped = !flipped },
                        contentAlignment = Alignment.Center
                    ) {
                        if (rotation <= 90f) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TAP TO REVEAL MEANING", color = Color(0xFFD4A017), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(24.dp))
                                Text(card.word, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                if (card.partOfSpeech.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("(${card.partOfSpeech})", color = Color(0xFFB9BDC7), fontSize = 14.sp)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .graphicsLayer { rotationY = 180f },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("MEANING", color = Color(0xFFB8862E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                Text(card.word, color = Color(0xFF12203D), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(10.dp))
                                Text(card.meaning, color = Color(0xFF2A2A2A), fontSize = 15.sp, textAlign = TextAlign.Center)
                                if (card.example.isNotBlank()) {
                                    Spacer(Modifier.height(14.dp))
                                    Column(
                                        modifier = Modifier
                                            .background(Color(0xFF12203D), RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text("EXAMPLE", color = Color(0xFFD4A017), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(card.example, color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (flipped) "Tap card to flip back" else "Tap card to flip",
                        color = Color(0xFF8A8E9E), fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(Modifier.weight(1f))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White, RoundedCornerShape(14.dp))
                                .border(1.5.dp, Color(0xFFC0392B), RoundedCornerShape(14.dp))
                                .clickable(enabled = flipped) { goNext(known = false) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("↻ Review Again", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1F7A3D), RoundedCornerShape(14.dp))
                                .clickable(enabled = flipped) { goNext(known = true) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("✓ Known", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardResultView(known: Int, review: Int, total: Int, onRestart: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 44.sp)
            Spacer(Modifier.height(10.dp))
            Text("Deck Complete!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            Spacer(Modifier.height(6.dp))
            Text("$known / $total marked as known", fontSize = 14.sp, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFFD4A017), RoundedCornerShape(100.dp))
                    .clickable(onClick = onRestart)
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) { Text("↻ Restart Deck", color = Color(0xFF12203D), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, Color(0xFF12203D), RoundedCornerShape(100.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) { Text("‹ Back to Home", color = Color(0xFF12203D), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}

package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.google.firebase.database.FirebaseDatabase

private data class AdminDeckOption(val key: String, val label: String)

private val FIXED_DECKS = listOf(
    AdminDeckOption("vocabulary", "Vocabulary"),
    AdminDeckOption("idioms", "Idioms & Phrases"),
    AdminDeckOption("literaryTerms", "Literary Terms"),
    AdminDeckOption("grammarRules", "Grammar Rules")
)

// Builds the raw stored text from a list of cards, renumbered in order.
private fun buildRawFromCards(cards: List<Flashcard>): String {
    return cards.mapIndexed { i, c ->
        val header = if (c.partOfSpeech.isNotBlank()) "${i + 1}. ${c.word} (${c.partOfSpeech})" else "${i + 1}. ${c.word}"
        buildString {
            append(header)
            append("\nMeaning: ").append(c.meaning)
            if (c.example.isNotBlank()) append("\nExample: ").append(c.example)
        }
    }.joinToString("\n\n")
}

@Composable
fun FlashcardAdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedDeckKey by remember { mutableStateOf(FIXED_DECKS[0].key) }
    var useCustomDeck by remember { mutableStateOf(false) }
    var customDeckKey by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    var existingCards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var loadingExisting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }

    val activeDeckKey = if (useCustomDeck) customDeckKey.trim() else selectedDeckKey

    fun loadExisting() {
        if (activeDeckKey.isBlank()) return
        loadingExisting = true
        FirebaseDatabase.getInstance().getReference("flashcardsPublic").child(activeDeckKey).child("raw")
            .get().addOnSuccessListener {
                loadingExisting = false
                existingCards = parseFlashcards(it.getValue(String::class.java) ?: "")
            }.addOnFailureListener { loadingExisting = false }
    }

    LaunchedEffect(activeDeckKey) { editingIndex = -1; loadExisting() }

    fun saveCards(newList: List<Flashcard>, successMsg: String) {
        saving = true
        val finalText = buildRawFromCards(newList)
        FirebaseDatabase.getInstance().getReference("flashcardsPublic").child(activeDeckKey).child("raw")
            .setValue(finalText)
            .addOnSuccessListener {
                saving = false
                Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                loadExisting()
            }
            .addOnFailureListener {
                saving = false
                Toast.makeText(context, "Failed, try again", Toast.LENGTH_LONG).show()
            }
    }

    val previewCount = remember(rawText) {
        rawText.split(Regex("\n(?=\\s*\\d+[.)]\\s)")).count { it.isNotBlank() && it.contains("Meaning:", ignoreCase = true) }
    }

    fun uploadNew(append: Boolean) {
        if (activeDeckKey.isBlank()) {
            Toast.makeText(context, "Deck ka naam/category chuno pehle", Toast.LENGTH_SHORT).show()
            return
        }
        if (rawText.isBlank()) {
            Toast.makeText(context, "Kuch to likho pehle", Toast.LENGTH_SHORT).show()
            return
        }
        val newCards = parseFlashcards(rawText)
        val finalList = if (append) existingCards + newCards else newCards
        saveCards(finalList, "Upload ho gaya ✓")
        rawText = ""
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
            Text("Manage Flashcards", color = Color(0xFFD4A017), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text("1. Choose deck", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FIXED_DECKS.forEach { deck ->
                    val selected = !useCustomDeck && selectedDeckKey == deck.key
                    Box(
                        modifier = Modifier
                            .background(if (selected) Color(0xFF12203D) else Color.White, RoundedCornerShape(100.dp))
                            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(100.dp))
                            .clickable { useCustomDeck = false; selectedDeckKey = deck.key }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(deck.label, color = if (selected) Color.White else Color(0xFF1A1A1A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .background(if (useCustomDeck) Color(0xFF12203D) else Color.White, RoundedCornerShape(100.dp))
                    .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(100.dp))
                    .clickable { useCustomDeck = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("+ Custom", color = if (useCustomDeck) Color.White else Color(0xFF1A1A1A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (useCustomDeck) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = customDeckKey,
                    onValueChange = { customDeckKey = it },
                    label = { Text("New deck name (e.g. examTerms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(22.dp))
            Text("2. Paste new flashcards", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            Spacer(Modifier.height(6.dp))
            Text(
                "Format: number, word (part of speech), Meaning: ..., Example: ... — one blank line between cards.",
                fontSize = 11.5.sp, color = Color(0xFF8A8E9E)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                placeholder = {
                    Text(
                        "1. Ubiquitous (adjective)\nMeaning: Present everywhere at the same time.\nExample: Smartphones have become ubiquitous in modern life.",
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Detected in this box: $previewCount card${if (previewCount == 1) "" else "s"}" +
                    if (activeDeckKey.isNotBlank()) "  •  Already saved in \"$activeDeckKey\": ${if (loadingExisting) "…" else "${existingCards.size}"}" else "",
                fontSize = 11.5.sp, color = Color(0xFF5B5F6B)
            )

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F7A3D), RoundedCornerShape(14.dp))
                    .clickable(enabled = !saving) { uploadNew(append = true) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (saving) "Uploading…" else "＋ Add to Existing Deck",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.5.dp, Color(0xFFC0392B), RoundedCornerShape(14.dp))
                    .clickable(enabled = !saving) { uploadNew(append = false) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("⚠ Replace Whole Deck With This", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(30.dp))
            Text(
                "3. Existing cards in \"$activeDeckKey\" (${existingCards.size})",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A)
            )
            Spacer(Modifier.height(10.dp))

            if (loadingExisting) {
                Text("Loading…", fontSize = 12.sp, color = Color(0xFF8A8E9E))
            } else if (existingCards.isEmpty()) {
                Text("Is deck mein abhi koi card nahi hai.", fontSize = 12.sp, color = Color(0xFF8A8E9E))
            } else {
                existingCards.forEachIndexed { index, card ->
                    ExistingCardRow(
                        card = card,
                        isEditing = editingIndex == index,
                        onEditToggle = { editingIndex = if (editingIndex == index) -1 else index },
                        onSave = { updated ->
                            val newList = existingCards.toMutableList().also { it[index] = updated }
                            saveCards(newList, "Card updated ✓")
                            editingIndex = -1
                        },
                        onDelete = {
                            val newList = existingCards.toMutableList().also { it.removeAt(index) }
                            saveCards(newList, "Card deleted ✓")
                            editingIndex = -1
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ExistingCardRow(
    card: Flashcard,
    isEditing: Boolean,
    onEditToggle: () -> Unit,
    onSave: (Flashcard) -> Unit,
    onDelete: () -> Unit
) {
    var word by remember(card, isEditing) { mutableStateOf(card.word) }
    var pos by remember(card, isEditing) { mutableStateOf(card.partOfSpeech) }
    var meaning by remember(card, isEditing) { mutableStateOf(card.meaning) }
    var example by remember(card, isEditing) { mutableStateOf(card.example) }
    var confirmingDelete by remember(isEditing) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        if (!isEditing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (card.partOfSpeech.isNotBlank()) "${card.word} (${card.partOfSpeech})" else card.word,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(card.meaning, fontSize = 12.sp, color = Color(0xFF5B5F6B), maxLines = 2)
                }
                Text(
                    "✎",
                    fontSize = 16.sp, color = Color(0xFF12203D),
                    modifier = Modifier.clickable { confirmingDelete = false; onEditToggle() }.padding(8.dp)
                )
                Text(
                    if (confirmingDelete) "Confirm?" else "🗑",
                    fontSize = if (confirmingDelete) 12.sp else 16.sp,
                    color = Color(0xFFC0392B),
                    fontWeight = if (confirmingDelete) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable {
                        if (confirmingDelete) onDelete() else confirmingDelete = true
                    }.padding(8.dp)
                )
            }
        } else {
            OutlinedTextField(
                value = word, onValueChange = { word = it },
                label = { Text("Word") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pos, onValueChange = { pos = it },
                label = { Text("Part of speech (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = meaning, onValueChange = { meaning = it },
                label = { Text("Meaning") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = example, onValueChange = { example = it },
                label = { Text("Example (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1F7A3D), RoundedCornerShape(10.dp))
                        .clickable {
                            if (word.isNotBlank() && meaning.isNotBlank()) {
                                onSave(card.copy(word = word.trim(), partOfSpeech = pos.trim(), meaning = meaning.trim(), example = example.trim()))
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0xFF8A8E9E), RoundedCornerShape(10.dp))
                        .clickable { onEditToggle() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Cancel", color = Color(0xFF5B5F6B), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
    }
}

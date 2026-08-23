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

@Composable
fun FlashcardAdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedDeckKey by remember { mutableStateOf(FIXED_DECKS[0].key) }
    var useCustomDeck by remember { mutableStateOf(false) }
    var customDeckKey by remember { mutableStateOf("") }
    var rawText by remember { mutableStateOf("") }
    var existingRaw by remember { mutableStateOf("") }
    var loadingExisting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val activeDeckKey = if (useCustomDeck) customDeckKey.trim() else selectedDeckKey

    fun loadExisting() {
        if (activeDeckKey.isBlank()) return
        loadingExisting = true
        FirebaseDatabase.getInstance().getReference("flashcardsPublic").child(activeDeckKey).child("raw")
            .get().addOnSuccessListener {
                loadingExisting = false
                existingRaw = it.getValue(String::class.java) ?: ""
            }.addOnFailureListener { loadingExisting = false }
    }

    LaunchedEffect(activeDeckKey) { loadExisting() }

    // Live preview count using the same parser the real screen uses
    val previewCount = remember(rawText) {
        rawText.split(Regex("\n(?=\\s*\\d+[.)]\\s)")).count { it.isNotBlank() && it.contains("Meaning:", ignoreCase = true) }
    }
    val existingCount = remember(existingRaw) {
        if (existingRaw.isBlank()) 0
        else existingRaw.split(Regex("\n(?=\\s*\\d+[.)]\\s)")).count { it.isNotBlank() && it.contains("Meaning:", ignoreCase = true) }
    }

    fun renumber(text: String): String {
        val blocks = text.split(Regex("\n(?=\\s*\\d+[.)]\\s)")).map { it.trim() }.filter { it.isNotEmpty() }
        return blocks.mapIndexed { i, block ->
            block.replaceFirst(Regex("^\\d+[.)]\\s*"), "${i + 1}. ")
        }.joinToString("\n\n")
    }

    fun upload(append: Boolean) {
        if (activeDeckKey.isBlank()) {
            Toast.makeText(context, "Deck ka naam/category chuno pehle", Toast.LENGTH_SHORT).show()
            return
        }
        if (rawText.isBlank()) {
            Toast.makeText(context, "Kuch to likho pehle", Toast.LENGTH_SHORT).show()
            return
        }
        saving = true
        val finalText = if (append && existingRaw.isNotBlank()) {
            renumber(existingRaw.trim() + "\n\n" + rawText.trim())
        } else {
            renumber(rawText.trim())
        }
        FirebaseDatabase.getInstance().getReference("flashcardsPublic").child(activeDeckKey).child("raw")
            .setValue(finalText)
            .addOnSuccessListener {
                saving = false
                Toast.makeText(context, "Upload ho gaya ✓", Toast.LENGTH_LONG).show()
                rawText = ""
                loadExisting()
            }
            .addOnFailureListener {
                saving = false
                Toast.makeText(context, "Upload fail hua, dobara try karo", Toast.LENGTH_LONG).show()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(if (useCustomDeck) Color(0xFF12203D) else Color.White, RoundedCornerShape(100.dp))
                        .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(100.dp))
                        .clickable { useCustomDeck = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("+ Custom", color = if (useCustomDeck) Color.White else Color(0xFF1A1A1A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
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
            Text("2. Paste flashcards", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
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
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Detected in this box: $previewCount card${if (previewCount == 1) "" else "s"}" +
                    if (activeDeckKey.isNotBlank()) "  •  Already saved in \"$activeDeckKey\": ${if (loadingExisting) "…" else "$existingCount"}" else "",
                fontSize = 11.5.sp, color = Color(0xFF5B5F6B)
            )

            Spacer(Modifier.height(20.dp))
            Text("3. Upload", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F7A3D), RoundedCornerShape(14.dp))
                    .clickable(enabled = !saving) { upload(append = true) }
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
                    .clickable(enabled = !saving) { upload(append = false) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("⚠ Replace Whole Deck", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "\"Add to Existing\" keeps old cards and appends new ones. \"Replace\" deletes everything already saved in this deck first.",
                fontSize = 11.sp, color = Color(0xFF8A8E9E)
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

private data class PremiumQuestion(
    val number: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: String
)

private fun parsePremiumQuestions(raw: String): List<PremiumQuestion> {
    return raw.trim().split(Regex("\n\\s*\n")).mapNotNull { block ->
        val lines = block.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return@mapNotNull null
        val firstLine = lines[0]
        val numMatch = Regex("^(\\d+)\\.\\s*(.*)").find(firstLine) ?: return@mapNotNull null
        val number = numMatch.groupValues[1]
        val question = numMatch.groupValues[2]
        val options = mutableListOf<String>()
        var correctAnswer = ""
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.startsWith("Correct Answer:")) {
                correctAnswer = line.removePrefix("Correct Answer:").trim()
            } else if (Regex("^[A-D]\\)").containsMatchIn(line)) {
                options.add(line)
            }
        }
        PremiumQuestion(number, question, options, correctAnswer)
    }
}

@Composable
fun PremiumSetReaderScreen(catKey: String, setKey: String, setTitle: String, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<PremiumQuestion>>(emptyList()) }
    var showAnswers by remember { mutableStateOf(true) }
    var selectedAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(catKey, setKey) {
        FirebaseDatabase.getInstance().getReference("paidPdfLibrary")
            .child(catKey).child("sets").child(setKey).child("questionsRaw")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                val raw = snapshot.getValue(String::class.java) ?: ""
                questions = parsePremiumQuestions(raw)
            }
            .addOnFailureListener { loading = false }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12203D))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ Back", color = Color.White) }
            Spacer(Modifier.width(8.dp))
            Text(setTitle, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (showAnswers) Color(0xFFD4A017) else Color.White)
                    .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(14.dp))
                    .clickable { showAnswers = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("✅ With Answer", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF12203D))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (!showAnswers) Color(0xFFD4A017) else Color.White)
                    .border(1.5.dp, Color(0xFFE3DFD3), RoundedCornerShape(14.dp))
                    .clickable { showAnswers = false; selectedAnswers = emptyMap() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("🎯 Without Answer", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF12203D))
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF12203D))
            }
        } else if (questions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text("No content uploaded yet.", color = Color(0xFF5B5F6B), fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(questions) { q ->
                    Column(modifier = Modifier.padding(bottom = 18.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF12203D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(q.number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(q.question, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(modifier = Modifier.padding(start = 36.dp)) {
                            val userSelected = selectedAnswers[q.number]
                            q.options.forEach { opt ->
                                val optLetter = opt.substringBefore(")").trim()
                                val correctLetter = q.correctAnswer.trim()
                                val isSelected = userSelected == optLetter
                                val isCorrectOption = optLetter == correctLetter

                                val bgColor = when {
                                    showAnswers -> Color.Transparent
                                    userSelected == null -> Color.Transparent
                                    isSelected && isCorrectOption -> Color(0xFFDCF5E0)
                                    isSelected && !isCorrectOption -> Color(0xFFFBE0DE)
                                    isCorrectOption -> Color(0xFFDCF5E0)
                                    else -> Color.Transparent
                                }
                                val textColor = when {
                                    showAnswers -> Color(0xFF5B5F6B)
                                    userSelected == null -> Color(0xFF5B5F6B)
                                    isSelected && isCorrectOption -> Color(0xFF1F7A3D)
                                    isSelected && !isCorrectOption -> Color(0xFFC0392B)
                                    isCorrectOption -> Color(0xFF1F7A3D)
                                    else -> Color(0xFF5B5F6B)
                                }
                                val borderColor = when {
                                    showAnswers -> Color(0xFFE3DFD3)
                                    bgColor == Color.Transparent -> Color(0xFFCFCAC0)
                                    else -> textColor
                                }
                                val boxBg = when {
                                    showAnswers -> Color.White
                                    bgColor == Color.Transparent -> Color(0xFFFAF8F3)
                                    else -> bgColor
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(boxBg)
                                        .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
                                        .clickable(enabled = !showAnswers && userSelected == null) {
                                            selectedAnswers = selectedAnswers + (q.number to optLetter)
                                        }
                                        .padding(vertical = 12.dp, horizontal = 14.dp)
                                ) {
                                    Text(
                                        opt,
                                        fontSize = 14.sp,
                                        color = if (showAnswers) Color(0xFF1A1A1A) else textColor,
                                        fontWeight = if (bgColor != Color.Transparent) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                            if (showAnswers && q.correctAnswer.isNotEmpty()) {
                                Text(
                                    "Correct Answer: ${q.correctAnswer}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1F7A3D),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFE3DFD3))
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

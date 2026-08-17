package com.shreeyog.engteck.screens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

data class ParsedQuestion(
    val number: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: String
)

private fun parseQuestions(raw: String): List<ParsedQuestion> {
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
        ParsedQuestion(number, question, options, correctAnswer)
    }
}

@Composable
fun SetDetailScreen(catKey: String, setKey: String, setTitle: String) {
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<ParsedQuestion>>(emptyList()) }
    var showAnswers by remember { mutableStateOf(true) }

    LaunchedEffect(catKey, setKey) {
        FirebaseDatabase.getInstance().getReference("studySets")
            .child(catKey).child("sets").child(setKey).child("questionsRaw")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                val raw = snapshot.getValue(String::class.java) ?: ""
                questions = parseQuestions(raw)
            }
            .addOnFailureListener { loading = false }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12203D))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(setTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // Toggle
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
                    .clickable { showAnswers = false }
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
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF12203D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(q.number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(q.question, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(modifier = Modifier.padding(start = 36.dp)) {
                            q.options.forEach { opt ->
                                Text(opt, fontSize = 14.sp, color = Color(0xFF5B5F6B), modifier = Modifier.padding(bottom = 4.dp))
                            }
                            if (showAnswers && q.correctAnswer.isNotEmpty()) {
                                Text(
                                    "Correct Answer: ${q.correctAnswer}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF5B5F6B),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFE3DFD3))
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

            // Bottom action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE85D4C))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↓ Download PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color(0xFFE3DFD3), RoundedCornerShape(14.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌐 Interactive Quiz", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

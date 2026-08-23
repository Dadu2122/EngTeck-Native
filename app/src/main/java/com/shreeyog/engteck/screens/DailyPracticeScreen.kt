package com.shreeyog.engteck.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.ui.theme.Gold
import com.shreeyog.engteck.ui.theme.InkSoft
import com.shreeyog.engteck.ui.theme.NavyDeep

// ---------- Shared parsing (mirrors the website's parseQuestions / pcSplitCorrectAnswer) ----------

data class DpQuestion(val number: Int, val question: String, val options: List<String>)
data class DpSplitAnswer(val options: List<String>, val answer: String?, val explanation: String)

data class DpPartInfo(val key: String, val label: String, val max: Int)
val DP_STUDENT_PARTS = listOf(
    DpPartInfo("theoryRaw", "Theories, Devices & Figures", 50),
    DpPartInfo("literatureRaw", "Literature", 50),
    DpPartInfo("mixedRaw", "Mixed — All Topics", 125)
)

fun parseDpQuestions(raw: String): List<DpQuestion> {
    if (raw.isBlank()) return emptyList()
    val parts = raw.split(Regex("\\n(?=\\s*Q?\\d+[.)]\\s)", RegexOption.IGNORE_CASE))
    return parts.map { it.trim() }.filter { it.isNotBlank() }.mapIndexed { i, block ->
        val lines = block.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val question = (lines.getOrNull(0) ?: "").replace(Regex("^Q?\\d+[.)]\\s*", RegexOption.IGNORE_CASE), "")
        val options = lines.drop(1)
        DpQuestion(i + 1, question, options)
    }
}

fun splitDpAnswer(options: List<String>): DpSplitAnswer {
    var answer: String? = null
    var explanation = ""
    val clean = mutableListOf<String>()
    for (o in options) {
        val m = Regex("^\\s*(?:ans(?:wer)?|correct\\s*answer)\\s*[:\\-]\\s*([A-Da-d])", RegexOption.IGNORE_CASE).find(o)
        if (m != null) { answer = m.groupValues[1].uppercase(); continue }
        val em = Regex("^Explanation:\\s*(.*)$", RegexOption.IGNORE_CASE).find(o)
        if (em != null) { explanation = em.groupValues[1]; continue }
        clean.add(o)
    }
    return DpSplitAnswer(clean, answer, explanation)
}

fun dpOptionLetter(opt: String, idx: Int): String {
    val m = Regex("^\\(?([A-Da-d])[.)]").find(opt)
    return m?.groupValues?.get(1)?.uppercase() ?: ('A' + idx).toString()
}

// ---------- Hub: 3 part-cards (Theory / Literature / Mixed) ----------

@Composable
fun DailyPracticeHubScreen(catKey: String, catLabel: String, onPartClick: (partKey: String, partLabel: String, isMixed: Boolean) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var dateStr by remember { mutableStateOf("") }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(catKey) {
        FirebaseDatabase.getInstance().getReference("dailyPractice").child(catKey)
            .get()
            .addOnSuccessListener { snap ->
                loading = false
                dateStr = snap.child("date").getValue(String::class.java) ?: ""
                val m = mutableMapOf<String, Int>()
                DP_STUDENT_PARTS.forEach { p -> m[p.key] = parseDpQuestions(snap.child(p.key).getValue(String::class.java) ?: "").size }
                counts = m
            }
            .addOnFailureListener { loading = false }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("🔥 Daily Practice", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(4.dp))
        Text(
            if (dateStr.isEmpty()) "$catLabel — coming soon" else "$catLabel — $dateStr",
            fontSize = 13.sp, color = InkSoft
        )
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyDeep)
            }
        } else {
            DP_STUDENT_PARTS.forEach { p ->
                val count = counts[p.key] ?: 0
                Card(
                    onClick = { onPartClick(p.key, p.label, p.key == "mixedRaw") },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, Gold),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(p.label, color = NavyDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("$count / ${p.max} Q", color = InkSoft, fontSize = 11.5.sp)
                        }
                        Text("›", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------- Theory / Literature viewer: With Answer / Without Answer + Answer Key ----------

@Composable
fun DailyPracticeViewerScreen(catKey: String, partKey: String, partLabel: String) {
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<DpQuestion>>(emptyList()) }
    var quizMode by remember { mutableStateOf(false) } // false = With Answer, true = Without Answer
    var showAnswerKey by remember { mutableStateOf(false) }
    var revealedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pickedLetters by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    LaunchedEffect(catKey, partKey) {
        FirebaseDatabase.getInstance().getReference("dailyPractice").child(catKey).child(partKey)
            .get()
            .addOnSuccessListener { snap ->
                loading = false
                questions = parseDpQuestions(snap.getValue(String::class.java) ?: "")
            }
            .addOnFailureListener { loading = false }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text(partLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(12.dp))

        if (loading) {
            CircularProgressIndicator(color = NavyDeep)
        } else if (questions.isEmpty()) {
            Text("Coming soon.", color = InkSoft, fontSize = 14.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                        .background(if (!quizMode) NavyDeep else Color(0xFFF5F3EC))
                        .clickable { quizMode = false }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("✅ With Answer", color = if (!quizMode) Color.White else InkSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                        .background(if (quizMode) NavyDeep else Color(0xFFF5F3EC))
                        .clickable { quizMode = true }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("🎯 Without Answer", color = if (quizMode) Color.White else InkSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(12.dp))

            val answerKey = questions.mapNotNull { q ->
                val split = splitDpAnswer(q.options)
                if (split.answer != null) q.number to split.answer else null
            }
            if (answerKey.isNotEmpty()) {
                TextButton(onClick = { showAnswerKey = !showAnswerKey }) {
                    Text(if (showAnswerKey) "▲ Hide Answer Key" else "✅ Show Answer Key", color = Color(0xFF1F7A3D), fontWeight = FontWeight.Bold)
                }
                if (showAnswerKey) {
                    DpAnswerKeyGrid(answerKey)
                }
                Spacer(Modifier.height(6.dp))
            }

            questions.forEachIndexed { idx, q ->
                val split = splitDpAnswer(q.options)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("${q.number}. ${q.question}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF1A1A1A))
                    Spacer(Modifier.height(8.dp))
                    if (!quizMode) {
                        split.options.forEach { o ->
                            Text(o, fontSize = 13.sp, color = Color(0xFF1A1A1A), modifier = Modifier.padding(vertical = 3.dp))
                        }
                        if (split.answer != null) {
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().background(Color(0xFFEAF6E9), RoundedCornerShape(8.dp)).padding(8.dp)) {
                                Text("Correct Answer: ${split.answer}", color = Color(0xFF1F7A3D), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    } else {
                        val revealed = revealedIndices.contains(idx)
                        split.options.forEachIndexed { oi, o ->
                            val letter = dpOptionLetter(o, oi)
                            val isCorrect = letter == split.answer
                            val picked = pickedLetters[idx] == letter
                            val bg = when {
                                !revealed -> Color(0xFFF5F3EC)
                                isCorrect -> Color(0xFFD9F2DD)
                                picked && !isCorrect -> Color(0xFFFBDCD9)
                                else -> Color(0xFFF5F3EC)
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .background(bg, RoundedCornerShape(8.dp))
                                    .clickable(enabled = !revealed) {
                                        pickedLetters = pickedLetters + (idx to letter)
                                        revealedIndices = revealedIndices + idx
                                    }
                                    .padding(10.dp)
                            ) { Text(o, fontSize = 13.sp, color = Color(0xFF1A1A1A)) }
                        }
                        if (revealed && split.explanation.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().background(Color(0xFFFCF3D9), RoundedCornerShape(8.dp)).padding(10.dp)) {
                                Column {
                                    Text("Solid Fact / Explanation", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF946B00))
                                    Spacer(Modifier.height(3.dp))
                                    Text(split.explanation, fontSize = 12.5.sp, color = Color(0xFF1A1A1A))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DpAnswerKeyGrid(answerKey: List<Pair<Int, String>>) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        answerKey.chunked(5).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                rowItems.forEach { (num, ans) ->
                    Box(
                        modifier = Modifier.weight(1f).background(Color(0xFFF5F3EC), RoundedCornerShape(8.dp)).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$num: $ans", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                    }
                }
                repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------- Mixed 125-Q: full scored test (start -> answer all -> submit -> result) ----------

@Composable
fun MixedTestScreen(catKey: String) {
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<DpQuestion>>(emptyList()) }
    var testStarted by remember { mutableStateOf(false) }
    var testSubmitted by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    LaunchedEffect(catKey) {
        FirebaseDatabase.getInstance().getReference("dailyPractice").child(catKey).child("mixedRaw")
            .get()
            .addOnSuccessListener { snap ->
                loading = false
                questions = parseDpQuestions(snap.getValue(String::class.java) ?: "")
            }
            .addOnFailureListener { loading = false }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NavyDeep) }
        return
    }
    val total = questions.size
    if (total == 0) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Mixed — All Topics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
            Spacer(Modifier.height(10.dp))
            Text("Coming soon.", color = InkSoft, fontSize = 14.sp)
        }
        return
    }

    if (!testStarted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🎯", fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text("Full Test — $total Questions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyDeep, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "All questions will appear on one page — answer as many as you like, then Submit at the end to see your result.",
                fontSize = 13.sp, color = InkSoft, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { testStarted = true },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = NavyDeep),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("▶ Start Test", fontWeight = FontWeight.Bold) }
        }
        return
    }

    if (testSubmitted && !showResult) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("✅", fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text("Test Submitted!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
            Spacer(Modifier.height(8.dp))
            Text("Tap the button below to see your result.", fontSize = 13.sp, color = InkSoft)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { showResult = true },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = NavyDeep),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("📊 Show My Result", fontWeight = FontWeight.Bold) }
        }
        return
    }

    if (testSubmitted && showResult) {
        MixedTestResultScreen(questions, answers)
        return
    }

    // Active test-taking screen
    val answeredCount = answers.size
    val progressPct = if (total > 0) (answeredCount * 100 / total) else 0
    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Mixed — All Topics ($total Q)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = progressPct / 100f,
            color = Gold,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(4.dp))
        Text("Answered $answeredCount / $total", fontSize = 11.5.sp, color = InkSoft)
        Spacer(Modifier.height(14.dp))

        questions.forEachIndexed { idx, q ->
            val split = splitDpAnswer(q.options)
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("${idx + 1}. ${q.question}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(8.dp))
                split.options.forEachIndexed { oi, o ->
                    val letter = dpOptionLetter(o, oi)
                    val picked = answers[idx] == letter
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .background(if (picked) Color(0xFFFCF3D9) else Color(0xFFF5F3EC), RoundedCornerShape(8.dp))
                            .border(if (picked) 1.5.dp else 0.dp, Gold, RoundedCornerShape(8.dp))
                            .clickable { answers = answers + (idx to letter) }
                            .padding(10.dp)
                    ) { Text(o, fontSize = 13.sp, color = Color(0xFF1A1A1A)) }
                }
            }
        }

        Button(
            onClick = { testSubmitted = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F7A3D), contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("✅ Submit Test", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun MixedTestResultScreen(questions: List<DpQuestion>, answers: Map<Int, String>) {
    val total = questions.size
    var attempted = 0
    var right = 0
    var wrong = 0
    val splits = questions.map { splitDpAnswer(it.options) }
    questions.forEachIndexed { idx, _ ->
        val picked = answers[idx]
        if (picked != null) {
            attempted++
            if (picked == splits[idx].answer) right++ else wrong++
        }
    }
    val scorePct = if (total > 0) (right * 100 / total) else 0

    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("📊 Your Result", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ResultStatBox("$right", "Correct", Color(0xFF1F7A3D), Modifier.weight(1f))
            ResultStatBox("$wrong", "Wrong", Color(0xFFC0392B), Modifier.weight(1f))
            ResultStatBox("${total - attempted}", "Skipped", Color(0xFF8A8F99), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F3EC), RoundedCornerShape(12.dp)).padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Score: $scorePct%  ($right / $total)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyDeep)
        }
        Spacer(Modifier.height(18.dp))
        Text("Review", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(10.dp))

        questions.forEachIndexed { idx, q ->
            val split = splits[idx]
            val picked = answers[idx]
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text("${idx + 1}. ${q.question}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(8.dp))
                split.options.forEachIndexed { oi, o ->
                    val letter = dpOptionLetter(o, oi)
                    val isCorrect = letter == split.answer
                    val isPicked = picked == letter
                    val bg = when {
                        isCorrect -> Color(0xFFD9F2DD)
                        isPicked && !isCorrect -> Color(0xFFFBDCD9)
                        else -> Color(0xFFF5F3EC)
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).background(bg, RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Text(o, fontSize = 13.sp, color = Color(0xFF1A1A1A))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultStatBox(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = InkSoft)
    }
}

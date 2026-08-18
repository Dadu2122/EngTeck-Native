package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PSM_NAVY = Color(0xFF12203D)
private val PSM_GOLD = Color(0xFFD4A017)
private val PSM_CORAL = Color(0xFFE85D4C)
private val PSM_TEAL = Color(0xFF1B6B79)
private val PSM_MAROON = Color(0xFF7A2E2E)
private val PSM_GREEN = Color(0xFF1F7A3D)
private val PSM_RED = Color(0xFFC0392B)

// ---------- Shared parsing — matches the web app's parseQuestions + pcSplitCorrectAnswer exactly:
// questions split on the next "N. " line (not blank lines), every subsequent line is an option
// unless it's a Correct Answer/Explanation line. ----------
private data class PsmQuestion(val number: String, val question: String, val options: List<String>, val correctAnswer: String)
private fun psmParseQuestions(raw: String): List<PsmQuestion> {
    if (raw.isBlank()) return emptyList()
    val parts = raw.split(Regex("\n(?=\\s*\\d+[.)]\\s)"))
    return parts.map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { i, block ->
        val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val question = (lines.getOrNull(0) ?: "").replace(Regex("^\\d+[.)]\\s*"), "")
        var correctAnswer = ""
        val cleanOptions = mutableListOf<String>()
        lines.drop(1).forEach { line ->
            val m = Regex("^Correct Answer:\\s*([A-D])", RegexOption.IGNORE_CASE).find(line)
            if (m != null) {
                correctAnswer = m.groupValues[1].uppercase()
            } else if (!line.startsWith("Explanation:", ignoreCase = true)) {
                cleanOptions.add(line)
            }
        }
        PsmQuestion((i + 1).toString(), question, cleanOptions, correctAnswer)
    }
}
private fun psmOptionLetter(opt: String, idx: Int): String {
    val m = Regex("^\\(?([A-Da-d])[.)]").find(opt)
    return if (m != null) m.groupValues[1].uppercase() else ('A' + idx).toString()
}
private fun psmOptionText(opt: String): String = opt.replace(Regex("^\\(?[A-Da-d][.)]\\s*"), "")

private data class PsmSectionDef(val key: String, val label: String)
private val PSM_HISTORY_KEY = "historyOfEnglishLiterature"
private val PSM_SECTION_DEFS = listOf(
    PsmSectionDef(PSM_HISTORY_KEY, "History of English Literature"),
    PsmSectionDef("formsOfLiterature", "Forms of Literature"),
    PsmSectionDef("literaryDevices", "Literary Term / Device"),
    PsmSectionDef("figuresOfSpeech", "Figure of Speech"),
    PsmSectionDef("literaryTheories", "Literary Theories"),
    PsmSectionDef("literaryMovements", "Literary Movements"),
    PsmSectionDef("grammar", "Grammar Section")
)
private val PSM_NOTES_SECTIONS = PSM_SECTION_DEFS.filter { it.key != PSM_HISTORY_KEY }
private val PSM_GROUPS = mapOf("formsOfLiterature" to listOf("Poetry", "Prose", "Drama", "Cross-Genre / Mixed Forms"))

private data class PsmWriterEntry(val key: String, val name: String)
private data class PsmWorkEntry(val key: String, val title: String, val type: String)
private data class PsmTopicPointEntry(val key: String, val title: String, val content: String, val group: String?)

private sealed class PsmView {
    object Home : PsmView()
    data class WriterDetail(val key: String, val name: String) : PsmView()
    data class Bio(val key: String, val name: String) : PsmView()
    data class Critical(val key: String, val name: String) : PsmView()
    data class WorksList(val writerKey: String, val writerName: String) : PsmView()
    data class WorkDetail(val writerKey: String, val workKey: String, val title: String) : PsmView()
    data class TopicPointsList(val sectionKey: String, val label: String) : PsmView()
    data class TopicPointDetail(val sectionKey: String, val pointKey: String, val title: String, val content: String) : PsmView()
    data class DailyPracticeQuiz(val partKey: String, val label: String) : PsmView()
    object SelfAssessment : PsmView()
}

@Composable
fun PremiumStudyMaterialScreen(catKey: String, catLabel: String, mobile: String, onExit: () -> Unit) {
    var view by remember { mutableStateOf<PsmView>(PsmView.Home) }

    Column(Modifier.fillMaxSize().background(Color(0xFFFAF8F3))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(PSM_NAVY).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                when (val v = view) {
                    is PsmView.Home -> onExit()
                    is PsmView.WriterDetail -> view = PsmView.Home
                    is PsmView.Bio -> view = PsmView.WriterDetail(v.key, v.name)
                    is PsmView.Critical -> view = PsmView.WriterDetail(v.key, v.name)
                    is PsmView.WorksList -> view = PsmView.WriterDetail(v.writerKey, v.writerName)
                    is PsmView.WorkDetail -> view = PsmView.WorksList(v.writerKey, "")
                    is PsmView.TopicPointsList -> view = PsmView.Home
                    is PsmView.TopicPointDetail -> view = PsmView.Home
                    is PsmView.DailyPracticeQuiz -> view = PsmView.Home
                    is PsmView.SelfAssessment -> view = PsmView.Home
                }
            }) { Text("‹ Back", color = Color.White) }
            Spacer(Modifier.width(4.dp))
            Text("$catLabel — Premium Study Material", color = PSM_GOLD, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        when (val v = view) {
            is PsmView.Home -> PsmHomeView(
                catKey = catKey,
                onOpenWriter = { key, name -> view = PsmView.WriterDetail(key, name) },
                onOpenHistoryPoint = { pointKey, title, content -> view = PsmView.TopicPointDetail(PSM_HISTORY_KEY, pointKey, title, content) },
                onOpenSection = { sectionKey, label -> view = PsmView.TopicPointsList(sectionKey, label) },
                onOpenDailyPractice = { partKey, label -> view = PsmView.DailyPracticeQuiz(partKey, label) },
                onOpenSelfAssessment = { view = PsmView.SelfAssessment }
            )
            is PsmView.WriterDetail -> PsmWriterDetailView(
                writerName = v.name,
                onBio = { view = PsmView.Bio(v.key, v.name) },
                onCritical = { view = PsmView.Critical(v.key, v.name) },
                onWorks = { view = PsmView.WorksList(v.key, v.name) }
            )
            is PsmView.Bio -> PsmTextFieldView(catKey, v.key, "biography", "${v.name} — Biography")
            is PsmView.Critical -> PsmTextFieldView(catKey, v.key, "criticalComments", "${v.name} — Critical Comments")
            is PsmView.WorksList -> PsmWorksListView(catKey, v.writerKey) { workKey, title -> view = PsmView.WorkDetail(v.writerKey, workKey, title) }
            is PsmView.WorkDetail -> PsmWorkDetailView(catKey, v.writerKey, v.workKey, v.title)
            is PsmView.TopicPointsList -> PsmTopicPointsListView(catKey, v.sectionKey, v.label) { pointKey, title, content ->
                view = PsmView.TopicPointDetail(v.sectionKey, pointKey, title, content)
            }
            is PsmView.TopicPointDetail -> PsmTopicPointDetailView(v.title, v.content)
            is PsmView.DailyPracticeQuiz -> PsmMcqPracticeView(catKey, "dailyPractice", v.partKey, v.label)
            is PsmView.SelfAssessment -> PsmSelfAssessmentView(catKey, mobile)
        }
    }
}

@Composable
private fun PsmHomeView(
    catKey: String,
    onOpenWriter: (String, String) -> Unit,
    onOpenHistoryPoint: (String, String, String) -> Unit,
    onOpenSection: (String, String) -> Unit,
    onOpenDailyPractice: (String, String) -> Unit,
    onOpenSelfAssessment: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var syllabus by remember { mutableStateOf("") }
    var writers by remember { mutableStateOf<List<PsmWriterEntry>>(emptyList()) }
    var historyPoints by remember { mutableStateOf<List<PsmTopicPointEntry>>(emptyList()) }
    var sectionCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var dpDate by remember { mutableStateOf("") }
    var dpCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var saCount by remember { mutableStateOf(0) }

    val dpParts = listOf(Triple("theoryRaw", "Theories, Devices & Figures", 50), Triple("literatureRaw", "Literature", 50), Triple("mixedRaw", "Mixed — All Topics", 125))

    LaunchedEffect(catKey) {
        val db = FirebaseDatabase.getInstance()
        db.getReference("premiumContent").child(catKey).get().addOnSuccessListener { s ->
            loading = false
            syllabus = s.child("syllabus").getValue(String::class.java) ?: ""
            writers = s.child("writers").children.mapNotNull { c ->
                val key = c.key ?: return@mapNotNull null
                PsmWriterEntry(key, c.child("name").getValue(String::class.java) ?: "Untitled")
            }
            val ts = s.child("topicSections")
            historyPoints = ts.child(PSM_HISTORY_KEY).child("points").children.mapNotNull { c ->
                val key = c.key ?: return@mapNotNull null
                PsmTopicPointEntry(key, c.child("title").getValue(String::class.java) ?: "", c.child("content").getValue(String::class.java) ?: "", null)
            }
            val counts = mutableMapOf<String, Int>()
            PSM_NOTES_SECTIONS.forEach { sec -> counts[sec.key] = ts.child(sec.key).child("points").childrenCount.toInt() }
            sectionCounts = counts
        }.addOnFailureListener { loading = false }

        db.getReference("dailyPractice").child(catKey).get().addOnSuccessListener { s ->
            dpDate = s.child("date").getValue(String::class.java) ?: ""
            val counts = mutableMapOf<String, Int>()
            dpParts.forEach { (key, _, _) -> counts[key] = psmParseQuestions(s.child(key).getValue(String::class.java) ?: "").size }
            dpCounts = counts
        }
        db.getReference("selfAssessment").child(catKey).child("raw").get().addOnSuccessListener { s ->
            saCount = psmParseQuestions(s.getValue(String::class.java) ?: "").size
        }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PSM_NAVY) }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(modifier = Modifier.fillMaxWidth().background(PSM_NAVY).padding(vertical = 18.dp)) {
            Text("Welcome to our premium class", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        if (syllabus.isNotBlank()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text("SYLLABUS", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = PSM_TEAL)
                Spacer(Modifier.height(10.dp))
                Text(syllabus, fontSize = 13.5.sp, color = Color(0xFF1A1A1A), lineHeight = 21.sp)
            }
        }

        if (writers.isNotEmpty()) {
            Text("Writers", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                writers.forEachIndexed { index, w ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.5.dp, PSM_GOLD, RoundedCornerShape(16.dp))
                            .clickable { onOpenWriter(w.key, w.name) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(30.dp).background(PSM_NAVY, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = PSM_GOLD, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(w.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                        Button(
                            onClick = { onOpenWriter(w.key, w.name) },
                            colors = ButtonDefaults.buttonColors(containerColor = PSM_CORAL),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Read Biography", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (historyPoints.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(vertical = 14.dp, horizontal = 16.dp)
            ) {
                Text("📜 History of English Literature", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(historyPoints) { index, p ->
                    val gradient = if (index % 2 == 0) Brush.linearGradient(listOf(PSM_TEAL, Color(0xFF0F4550)))
                        else Brush.linearGradient(listOf(PSM_MAROON, Color(0xFF4A1414)))
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .background(gradient, RoundedCornerShape(16.dp))
                            .border(1.5.dp, PSM_GOLD, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(p.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.height(64.dp))
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .clickable { onOpenHistoryPoint(p.key, p.title, p.content) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("READ →", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text("Topic-wise Notes", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PSM_NOTES_SECTIONS.forEach { sec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.5.dp, PSM_GOLD, RoundedCornerShape(14.dp))
                        .clickable { onOpenSection(sec.key, sec.label) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sec.label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    Text("${sectionCounts[sec.key] ?: 0} topics ›", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PSM_GOLD)
                }
            }
        }

        Text("Daily Practice — 225 Questions", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            dpParts.forEach { (key, label, max) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0xFFFCF3D9), RoundedCornerShape(14.dp))
                        .border(1.5.dp, PSM_GOLD, RoundedCornerShape(14.dp))
                        .clickable { onOpenDailyPractice(key, label) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                    Text(
                        "${dpCounts[key] ?: 0} / $max Q${if (dpDate.isNotEmpty()) " · $dpDate" else ""} ›",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF946B00)
                    )
                }
            }
        }

        Text("Self Assessment", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .background(PSM_NAVY, RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Self Assessment", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("$saCount Questions • 30 sec per question", color = PSM_GOLD, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpenSelfAssessment,
                colors = ButtonDefaults.buttonColors(containerColor = PSM_CORAL),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("▶ Start Test", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .background(PSM_NAVY, RoundedCornerShape(18.dp))
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PSM_MAROON, RoundedCornerShape(12.dp))
                    .padding(vertical = 16.dp)
            ) {
                Text("📊 Progress Analytics", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PsmWriterDetailView(writerName: String, onBio: () -> Unit, onCritical: () -> Unit, onWorks: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(writerName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(18.dp))
        PsmHomeButton("📝 Biography", "Life, background & career", onBio)
        PsmHomeButton("💬 Critical Comments", "What critics & scholars said", onCritical)
        PsmHomeButton("📚 Works", "Novels, Drama, Poems, Sonnets & more", onWorks)
    }
}

@Composable
private fun PsmHomeButton(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.5.dp, PSM_GOLD, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.5.sp, color = Color(0xFF5B5F6B))
        }
        Text("›", fontSize = 22.sp, color = PSM_CORAL, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PsmTextFieldView(catKey: String, writerKey: String, field: String, title: String) {
    var loading by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
            .child("writers").child(writerKey).child(field)
            .get().addOnSuccessListener { text = it.getValue(String::class.java) ?: ""; loading = false }
            .addOnFailureListener { loading = false }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(14.dp))
        if (loading) {
            CircularProgressIndicator(color = PSM_NAVY)
        } else if (text.isBlank()) {
            Text("Abhi content nahi hai.", fontSize = 13.sp, color = Color(0xFF5B5F6B))
        } else {
            LazyColumn { item { Text(text, fontSize = 14.sp, color = Color(0xFF1A1A1A), lineHeight = 22.sp) } }
        }
    }
}

@Composable
private fun PsmWorksListView(catKey: String, writerKey: String, onOpen: (String, String) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var works by remember { mutableStateOf<List<PsmWorkEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
            .child("writers").child(writerKey).child("works")
            .get().addOnSuccessListener { snapshot ->
                loading = false
                works = snapshot.children.mapNotNull { c ->
                    val key = c.key ?: return@mapNotNull null
                    PsmWorkEntry(key, c.child("title").getValue(String::class.java) ?: "Untitled", c.child("type").getValue(String::class.java) ?: "individual")
                }
            }.addOnFailureListener { loading = false }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        if (loading) {
            CircularProgressIndicator(color = PSM_NAVY)
        } else if (works.isEmpty()) {
            Text("Koi works abhi add nahi hue.", fontSize = 13.sp, color = Color(0xFF5B5F6B))
        } else {
            val grouped = WORK_TYPE_SECTIONS.map { sec -> sec to works.filter { it.type == sec.key } }.filter { it.second.isNotEmpty() }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                grouped.forEach { (sec, list) ->
                    item {
                        Text(sec.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PSM_TEAL, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    }
                    items(list) { w ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(12.dp))
                                .clickable { onOpen(w.key, w.title) }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(w.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                            Text("›", fontSize = 16.sp, color = PSM_CORAL)
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PsmWorkDetailView(catKey: String, writerKey: String, workKey: String, title: String) {
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf("") }
    var characters by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf("") }
    var themes by remember { mutableStateOf("") }
    var questions by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf("summary") }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
            .child("writers").child(writerKey).child("works").child(workKey)
            .get().addOnSuccessListener { s ->
                summary = s.child("summary").getValue(String::class.java) ?: ""
                characters = s.child("characters").getValue(String::class.java) ?: ""
                lines = s.child("lines").getValue(String::class.java) ?: ""
                themes = s.child("themes").getValue(String::class.java) ?: ""
                questions = s.child("questions").getValue(String::class.java) ?: ""
                loading = false
            }.addOnFailureListener { loading = false }
    }

    Column(Modifier.fillMaxSize()) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A), modifier = Modifier.padding(20.dp))
        Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("summary" to "Summary", "characters" to "Characters", "lines" to "Lines", "themes" to "Themes", "questions" to "MCQs").forEach { (key, label) ->
                val active = activeTab == key
                Box(
                    modifier = Modifier
                        .background(if (active) PSM_TEAL else Color(0xFFF5F3EC), RoundedCornerShape(100.dp))
                        .clickable { activeTab = key }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PSM_NAVY) }
        } else if (activeTab == "questions") {
            PsmInlineMcqList(psmParseQuestions(questions), modifier = Modifier.weight(1f).padding(horizontal = 20.dp))
        } else {
            val text = when (activeTab) {
                "summary" -> summary
                "characters" -> characters
                "lines" -> lines
                "themes" -> themes
                else -> ""
            }
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                item {
                    if (text.isBlank()) Text("Abhi content nahi hai.", fontSize = 13.sp, color = Color(0xFF5B5F6B))
                    else Text(text, fontSize = 14.sp, color = Color(0xFF1A1A1A), lineHeight = 22.sp)
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun PsmTopicPointsListView(catKey: String, sectionKey: String, labelIn: String, onOpen: (String, String, String) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var points by remember { mutableStateOf<List<PsmTopicPointEntry>>(emptyList()) }
    val label = PSM_SECTION_DEFS.find { it.key == sectionKey }?.label ?: labelIn

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
            .child("topicSections").child(sectionKey).child("points")
            .get().addOnSuccessListener { snapshot ->
                loading = false
                points = snapshot.children.mapNotNull { c ->
                    val key = c.key ?: return@mapNotNull null
                    PsmTopicPointEntry(
                        key,
                        c.child("title").getValue(String::class.java) ?: "",
                        c.child("content").getValue(String::class.java) ?: "",
                        c.child("group").getValue(String::class.java)
                    )
                }
            }.addOnFailureListener { loading = false }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(14.dp))
        if (loading) {
            CircularProgressIndicator(color = PSM_NAVY)
        } else if (points.isEmpty()) {
            Text("Abhi points add nahi hue.", fontSize = 13.sp, color = Color(0xFF5B5F6B))
        } else {
            val groups = PSM_GROUPS[sectionKey]
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (groups != null) {
                    val ungrouped = points.filter { it.group == null || it.group !in groups }
                    val allBuckets = groups.map { g -> g to points.filter { it.group == g } } +
                        (if (ungrouped.isNotEmpty()) listOf("Ungrouped" to ungrouped) else emptyList())
                    allBuckets.forEach { (g, pts) ->
                        if (pts.isNotEmpty()) {
                            item { Text(g, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PSM_TEAL, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                            items(pts) { p -> PsmPointRow(p) { onOpen(p.key, p.title, p.content) } }
                        }
                    }
                } else {
                    items(points) { p -> PsmPointRow(p) { onOpen(p.key, p.title, p.content) } }
                }
            }
        }
    }
}

@Composable
private fun PsmPointRow(p: PsmTopicPointEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(p.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        Text("›", fontSize = 16.sp, color = PSM_CORAL)
    }
}

@Composable
private fun PsmTopicPointDetailView(title: String, content: String) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(14.dp))
        LazyColumn {
            item {
                if (content.isBlank()) Text("Abhi content nahi hai.", fontSize = 13.sp, color = Color(0xFF5B5F6B))
                else Text(content, fontSize = 14.sp, color = Color(0xFF1A1A1A), lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun PsmMcqPracticeView(catKey: String, firebaseRoot: String, partOrSetKey: String, label: String) {
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<PsmQuestion>>(emptyList()) }
    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference(firebaseRoot).child(catKey).child(partOrSetKey)
            .get().addOnSuccessListener {
                loading = false
                questions = psmParseQuestions(it.getValue(String::class.java) ?: "")
            }.addOnFailureListener { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), modifier = Modifier.padding(20.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PSM_NAVY) }
        } else {
            PsmInlineMcqList(questions, modifier = Modifier.weight(1f).padding(horizontal = 20.dp))
        }
    }
}

@Composable
private fun PsmInlineMcqList(questions: List<PsmQuestion>, modifier: Modifier = Modifier) {
    if (questions.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Abhi content nahi hai.", fontSize = 13.sp, color = Color(0xFF5B5F6B)) }
        return
    }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(questions) { q ->
            Column(modifier = Modifier.padding(bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.size(24.dp).background(PSM_NAVY, CircleShape), contentAlignment = Alignment.Center) {
                        Text(q.number, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(q.question, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                }
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.padding(start = 34.dp)) {
                    q.options.forEachIndexed { optIdx, opt ->
                        val letter = psmOptionLetter(opt, optIdx)
                        val isCorrect = letter == q.correctAnswer.trim()
                        Text(
                            "$letter) ${psmOptionText(opt)}",
                            fontSize = 13.sp,
                            color = if (isCorrect) PSM_GREEN else Color(0xFF5B5F6B),
                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

private const val SA_TIME_PER_Q = 30

@Composable
private fun PsmSelfAssessmentView(catKey: String, mobile: String) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<PsmQuestion>>(emptyList()) }
    var stage by remember { mutableStateOf("intro") }
    var idx by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var correctCount by remember { mutableStateOf(0) }
    var wrongCount by remember { mutableStateOf(0) }
    var skippedCount by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableStateOf(SA_TIME_PER_Q) }
    var answered by remember { mutableStateOf(false) }
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var timerTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("selfAssessment").child(catKey).child("raw")
            .get().addOnSuccessListener {
                loading = false
                questions = psmParseQuestions(it.getValue(String::class.java) ?: "")
            }.addOnFailureListener { loading = false }
    }

    fun lockAnswer(tapped: String?) {
        if (answered) return
        answered = true
        val q = questions.getOrNull(idx)
        val correct = q?.correctAnswer?.trim()
        if (tapped != null && correct != null && tapped == correct) { score++; correctCount++ }
        else if (tapped != null) wrongCount++
        else skippedCount++
        scope.launch {
            delay(900)
            if (idx + 1 >= questions.size) {
                stage = "result"
            } else {
                idx++
                answered = false
                selectedLetter = null
                timeLeft = SA_TIME_PER_Q
                timerTick++
            }
        }
    }

    LaunchedEffect(stage, timerTick) {
        if (stage != "quiz") return@LaunchedEffect
        while (timeLeft > 0 && !answered) {
            delay(1000)
            timeLeft--
        }
        if (timeLeft <= 0 && !answered) lockAnswer(null)
    }

    LaunchedEffect(stage) {
        if (stage == "result" && mobile.isNotEmpty()) {
            val total = questions.size
            val pct = if (total > 0) (score * 100 / total) else 0
            val scoreRef = FirebaseDatabase.getInstance().getReference("saScores").child(catKey).child(mobile)
            scoreRef.get().addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    scoreRef.setValue(mapOf("name" to "Student", "score" to score, "total" to total, "pct" to pct, "ts" to System.currentTimeMillis()))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(20.dp)) {
        when {
            loading -> CircularProgressIndicator(color = PSM_NAVY, modifier = Modifier.align(Alignment.Center))
            questions.isEmpty() -> Text("Self Assessment abhi ready nahi hai.", fontSize = 13.sp, color = Color(0xFF5B5F6B), modifier = Modifier.align(Alignment.Center))
            stage == "intro" -> Column(Modifier.fillMaxWidth().align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎯", fontSize = 44.sp)
                Spacer(Modifier.height(10.dp))
                Text("Self Assessment", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(10.dp))
                Text("${questions.size} Questions · $SA_TIME_PER_Q seconds per question", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Timer khatam hote hi agla sawaal apne aap aa jayega — jitna ho sake jaldi aur sahi answer karo.",
                    fontSize = 12.sp, color = Color(0xFF5B5F6B), textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        idx = 0; score = 0; correctCount = 0; wrongCount = 0; skippedCount = 0
                        timeLeft = SA_TIME_PER_Q; answered = false; selectedLetter = null
                        stage = "quiz"; timerTick++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PSM_CORAL),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.width(180.dp).height(48.dp)
                ) { Text("▶ Start", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            stage == "quiz" -> {
                val q = questions[idx]
                Column(Modifier.fillMaxSize()) {
                    Text("Question ${idx + 1} of ${questions.size}", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { idx / questions.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = PSM_GOLD, trackColor = Color(0xFFE3DFD3)
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (timeLeft <= 10) PSM_RED else PSM_NAVY, CircleShape)
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$timeLeft", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Q${idx + 1}.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PSM_TEAL)
                    Text(q.question, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(q.options) { optIdx, opt ->
                            val letter = psmOptionLetter(opt, optIdx)
                            val correct = q.correctAnswer.trim()
                            val bg = when {
                                !answered -> Color.White
                                letter == correct -> Color(0xFFDCF5E0)
                                letter == selectedLetter -> Color(0xFFFBE0DE)
                                else -> Color.White
                            }
                            val border = when {
                                !answered -> Color(0xFFE3DFD3)
                                letter == correct -> PSM_GREEN
                                letter == selectedLetter -> PSM_RED
                                else -> Color(0xFFE3DFD3)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg, RoundedCornerShape(12.dp))
                                    .border(1.5.dp, border, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !answered) { selectedLetter = letter; lockAnswer(letter) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(28.dp).background(Color(0xFFF5F3EC), CircleShape), contentAlignment = Alignment.Center) {
                                    Text(letter, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PSM_NAVY)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(psmOptionText(opt), fontSize = 14.sp, color = Color(0xFF1A1A1A))
                            }
                        }
                    }
                }
            }
            stage == "result" -> {
                val total = questions.size
                val pct = if (total > 0) (score * 100 / total) else 0
                val (emoji, label) = when {
                    pct >= 90 -> "🏆" to "Outstanding!"
                    pct >= 70 -> "🎉" to "Excellent!"
                    pct >= 50 -> "👍" to "Good!"
                    else -> "💪" to "Keep Practicing!"
                }
                Column(Modifier.fillMaxWidth().align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(emoji, fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(label, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
                    Spacer(Modifier.height(8.dp))
                    Text("Score: $score / $total ($pct%)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        PsmResultStat(correctCount, "Correct", PSM_GREEN)
                        PsmResultStat(wrongCount, "Wrong", PSM_RED)
                        PsmResultStat(skippedCount, "Skipped", Color(0xFF8A8A8A))
                    }
                    Spacer(Modifier.height(26.dp))
                    Button(
                        onClick = { stage = "intro" },
                        colors = ButtonDefaults.buttonColors(containerColor = PSM_CORAL),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.width(200.dp).height(46.dp)
                    ) { Text("↻ Retake Test", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun PsmResultStat(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 11.sp, color = Color(0xFF5B5F6B))
    }
}

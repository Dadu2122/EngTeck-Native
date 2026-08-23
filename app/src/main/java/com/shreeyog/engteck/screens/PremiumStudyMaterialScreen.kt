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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
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

private fun psmSaveTextPdf(context: android.content.Context, title: String, body: String): String? {
    return try {
        val pageWidth = 595
        val pageHeight = 842
        val document = android.graphics.pdf.PdfDocument()
        val titlePaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 16f; isFakeBoldText = true }
        val bandPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x12, 0x20, 0x3D) }
        val bodyPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x1A, 0x1A, 0x1A); textSize = 12f }
        val margin = 30f
        val contentWidth = pageWidth - margin * 2

        fun wrapText(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
            if (text.isEmpty()) return listOf("")
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var cur = StringBuilder()
            for (w in words) {
                val test = if (cur.isEmpty()) w else "$cur $w"
                if (paint.measureText(test) > maxWidth && cur.isNotEmpty()) { lines.add(cur.toString()); cur = StringBuilder(w) }
                else cur = StringBuilder(test)
            }
            if (cur.isNotEmpty()) lines.add(cur.toString())
            return lines
        }

        val allLines = mutableListOf<String>()
        body.split("\n").forEach { p -> allLines.addAll(wrapText(p, bodyPaint, contentWidth)) }

        var pageNumber = 1
        var page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 50f, bandPaint)
        canvas.drawText(title, margin, 32f, titlePaint)
        var y = 74f
        val lineHeight = 16f
        for (line in allLines) {
            if (y > 800f) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 40f
            }
            canvas.drawText(line, margin, y, bodyPaint)
            y += lineHeight
        }
        document.finishPage(page)

        val safeTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "$safeTitle.pdf"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> document.writeTo(out) }
                document.close()
                "Downloads/$fileName"
            } else {
                document.close(); null
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            java.io.FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun PsmDownloadPdfButton(title: String, body: String) {
    val context = LocalContext.current
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = {
            val path = psmSaveTextPdf(context, title, body)
            android.widget.Toast.makeText(
                context,
                if (path != null) "Saved to $path" else "Download failed, try again",
                android.widget.Toast.LENGTH_LONG
            ).show()
        },
        colors = ButtonDefaults.buttonColors(containerColor = PSM_TEAL),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        Text("⬇ Download PDF", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private fun psmWrapLines(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
    if (text.isEmpty()) return listOf("")
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var cur = StringBuilder()
    for (w in words) {
        val test = if (cur.isEmpty()) w else "$cur $w"
        if (paint.measureText(test) > maxWidth && cur.isNotEmpty()) { lines.add(cur.toString()); cur = StringBuilder(w) }
        else cur = StringBuilder(test)
    }
    if (cur.isNotEmpty()) lines.add(cur.toString())
    return lines
}

private fun psmSaveMcqPdf(context: android.content.Context, title: String, questions: List<PsmQuestion>): String? {
    return try {
        val pageWidth = 595
        val pageHeight = 842
        val document = android.graphics.pdf.PdfDocument()
        val margin = 30f
        val contentWidth = pageWidth - margin * 2 - 20f
        val lineHeight = 14.5f

        val titlePaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 17f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }
        val subtitlePaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 10.5f; textAlign = android.graphics.Paint.Align.CENTER }
        val bandPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x12, 0x20, 0x3D) }
        val qPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x1A, 0x1A, 0x1A); textSize = 12.5f; isFakeBoldText = true }
        val optPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x1A, 0x1A, 0x1A); textSize = 11.5f }
        val correctPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x1F, 0x7A, 0x3D); textSize = 11.5f; isFakeBoldText = true }
        val correctBoxPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xEA, 0xF6, 0xE9) }
        val correctBorderPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x2E, 0x9B, 0x53); style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f }
        val normalBoxPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xFA, 0xF8, 0xF3) }
        val normalBorderPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xD9, 0xD3, 0xC4); style = android.graphics.Paint.Style.STROKE; strokeWidth = 1.5f }
        val explLabelPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xD4, 0xA0, 0x17); textSize = 10f; isFakeBoldText = true }
        val explBodyPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 11f }
        val explBoxPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x12, 0x20, 0x3D) }
        val footerRightPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x8A, 0x8A, 0x8A); textSize = 9.5f; textAlign = android.graphics.Paint.Align.RIGHT }
        val watermarkPaint = android.graphics.Paint().apply { color = android.graphics.Color.argb(14, 0, 0, 0); textSize = 40f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }

        // Every space check below (ensureSpace) and the matching y-advance use the
        // SAME amount, so a dry-run pass and the real draw pass always break pages
        // in exactly the same places — that's what lets the footer show a correct
        // "Page X of Y" even though PdfDocument pages can't be redrawn once closed.
        fun blockNeeds(q: PsmQuestion): List<Float> {
            val needs = mutableListOf<Float>()
            val qLines = psmWrapLines("${q.number}. ${q.question}", qPaint, contentWidth)
            needs.add(qLines.size * lineHeight + 10f)
            q.options.forEachIndexed { oi, opt ->
                val letter = psmOptionLetter(opt, oi)
                val text = psmOptionText(opt)
                val optLines = psmWrapLines("$letter) $text", optPaint, contentWidth - 20f)
                needs.add(optLines.size * lineHeight + 16f + 4f)
            }
            if (q.explanation.isNotBlank()) {
                val explLines = psmWrapLines(q.explanation, explBodyPaint, contentWidth - 20f)
                needs.add((explLines.size + 1) * lineHeight + 8f + 6f)
            }
            return needs
        }

        var totalPages = 1
        run {
            var simY = 80f
            questions.forEach { q ->
                blockNeeds(q).forEach { need ->
                    if (simY + need > 810f) { totalPages++; simY = 46f }
                    simY += need
                }
                simY += 12f
            }
        }

        var pageNumber = 1
        var page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        fun drawWatermark(cv: android.graphics.Canvas) {
            cv.save()
            cv.rotate(-28f, pageWidth / 2f, pageHeight / 2f)
            for (row in -2..4) {
                cv.drawText("Shree English Classes", pageWidth / 2f, pageHeight / 2f + row * 140f, watermarkPaint)
            }
            cv.restore()
        }
        fun drawFooter(cv: android.graphics.Canvas) {
            cv.drawText("Page $pageNumber of $totalPages", pageWidth - margin, pageHeight - 20f, footerRightPaint)
        }
        fun drawHeader(showSubtitle: Boolean) {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), if (showSubtitle) 58f else 34f, bandPaint)
            canvas.drawText(title, pageWidth / 2f, 26f, titlePaint)
            if (showSubtitle) canvas.drawText("Shree English Classes • Green = Correct Answer", pageWidth / 2f, 46f, subtitlePaint)
        }
        drawWatermark(canvas)
        drawHeader(true)
        var y = 80f

        fun ensureSpace(needed: Float) {
            if (y + needed > 810f) {
                drawFooter(canvas)
                document.finishPage(page)
                pageNumber++
                page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawWatermark(canvas)
                drawHeader(false)
                y = 46f
            }
        }

        questions.forEach { q ->
            val qLines = psmWrapLines("${q.number}. ${q.question}", qPaint, contentWidth)
            val qNeed = qLines.size * lineHeight + 10f
            ensureSpace(qNeed)
            qLines.forEachIndexed { i, line -> canvas.drawText(line, margin, y + (i * lineHeight), qPaint) }
            y += qNeed

            q.options.forEachIndexed { oi, opt ->
                val letter = psmOptionLetter(opt, oi)
                val text = psmOptionText(opt)
                val isCorrect = letter == q.correctAnswer.trim()
                val optLines = psmWrapLines("$letter) $text", optPaint, contentWidth - 20f)
                val boxHeight = optLines.size * lineHeight + 16f
                val optNeed = boxHeight + 4f
                ensureSpace(optNeed)
                val boxTop = y - 14f
                val boxPaint = if (isCorrect) correctBoxPaint else normalBoxPaint
                val borderPaint = if (isCorrect) correctBorderPaint else normalBorderPaint
                val boxRect = android.graphics.RectF(margin + 14f, boxTop, pageWidth - margin, boxTop + boxHeight)
                canvas.drawRoundRect(boxRect, 6f, 6f, boxPaint)
                canvas.drawRoundRect(boxRect, 6f, 6f, borderPaint)
                val txtPaint = if (isCorrect) correctPaint else optPaint
                optLines.forEachIndexed { i, line -> canvas.drawText(line, margin + 22f, y + (i * lineHeight), txtPaint) }
                y += optNeed
            }

            if (q.explanation.isNotBlank()) {
                val explLines = psmWrapLines(q.explanation, explBodyPaint, contentWidth - 20f)
                val boxHeight = (explLines.size + 1) * lineHeight + 8f
                val explNeed = boxHeight + 6f
                ensureSpace(explNeed)
                canvas.drawRoundRect(android.graphics.RectF(margin + 14f, y - 10f, pageWidth - margin, y - 10f + boxHeight), 6f, 6f, explBoxPaint)
                canvas.drawText("SOLID FACT / EXPLANATION", margin + 22f, y, explLabelPaint)
                explLines.forEachIndexed { i, line -> canvas.drawText(line, margin + 22f, y + lineHeight + (i * lineHeight), explBodyPaint) }
                y += explNeed
            }
            y += 12f
        }
        drawFooter(canvas)
        document.finishPage(page)

        val safeTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "$safeTitle.pdf"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> document.writeTo(out) }
                document.close()
                "Downloads/$fileName"
            } else { document.close(); null }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            java.io.FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            file.absolutePath
        }
    } catch (e: Exception) { null }
}

@Composable
private fun PsmDownloadMcqPdfButton(title: String, questions: List<PsmQuestion>) {
    val context = LocalContext.current
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = {
            val path = psmSaveMcqPdf(context, title, questions)
            android.widget.Toast.makeText(
                context,
                if (path != null) "Saved to $path" else "Download failed, try again",
                android.widget.Toast.LENGTH_LONG
            ).show()
        },
        colors = ButtonDefaults.buttonColors(containerColor = PSM_CORAL),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        Text("⬇ Download PDF", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private val PSM_ANNOTATE_COLORS = listOf(
    "red" to Color(0xFFE85D4C),
    "navy" to PSM_NAVY,
    "green" to Color(0xFF1F9D55)
)
private val PSM_HIGHLIGHT_COLOR = Color(0xFFFFF3A3)

@Composable
private fun PsmAnnotatableContent(context: android.content.Context, contentKey: String, title: String, body: String) {
    val prefs = remember { context.getSharedPreferences("engteck_prefs", android.content.Context.MODE_PRIVATE) }
    val annotKey = "psm_annot_$contentKey"
    val lines = remember(body) { body.split("\n") }

    var annotations by remember(contentKey) {
        mutableStateOf(
            (prefs.getString(annotKey, "") ?: "")
                .split(",").filter { it.contains(":") }
                .associate { val (i, c) = it.split(":"); i.toInt() to c }
                .toMutableMap()
        )
    }
    var activeTool by remember { mutableStateOf<String?>(null) }

    fun persist() {
        prefs.edit().putString(annotKey, annotations.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.5.dp, PSM_GOLD, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        if (body.isBlank()) {
            Text("Coming soon.", fontSize = 13.sp, color = Color(0xFF5B5F6B))
        } else {
            lines.forEachIndexed { index, line ->
                if (line.isBlank()) {
                    Spacer(Modifier.height(10.dp))
                } else {
                    val annotColor = annotations[index]
                    val bg = when (annotColor) {
                        "red" -> Color(0xFFE85D4C).copy(alpha = 0.15f)
                        "navy" -> PSM_NAVY.copy(alpha = 0.12f)
                        "green" -> Color(0xFF1F9D55).copy(alpha = 0.15f)
                        "highlight" -> PSM_HIGHLIGHT_COLOR
                        else -> Color.Transparent
                    }
                    JustifiedText(
                        line,
                        fontSize = 14.sp,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg, RoundedCornerShape(4.dp))
                            .clickable(enabled = activeTool != null) {
                                if (activeTool != null) {
                                    if (annotations[index] == activeTool) annotations.remove(index) else annotations[index] = activeTool!!
                                    annotations = annotations.toMutableMap()
                                    persist()
                                }
                            }
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        if (activeTool == null) "Rang ya highlight select karo, phir jis line pe lagana hai use tap karo."
        else "Ab jis line pe rang lagana hai use tap karo — dobara tap karke hataya bhi ja sakta hai.",
        fontSize = 10.5.sp, color = Color(0xFF9B968A)
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PSM_ANNOTATE_COLORS.forEach { (key, color) ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, CircleShape)
                    .border(if (activeTool == key) 3.dp else 0.dp, Color(0xFF1A1A1A), CircleShape)
                    .clickable { activeTool = if (activeTool == key) null else key }
            )
        }
        Box(
            modifier = Modifier
                .background(if (activeTool == "highlight") PSM_HIGHLIGHT_COLOR else Color(0xFFFCF3D9), RoundedCornerShape(100.dp))
                .border(if (activeTool == "highlight") 2.dp else 0.dp, Color(0xFF1A1A1A), RoundedCornerShape(100.dp))
                .clickable { activeTool = if (activeTool == "highlight") null else "highlight" }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("🖍️ Highlight", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF946B00))
        }
    }
    Spacer(Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(100.dp))
            .border(1.5.dp, Color(0xFFE85D4C), RoundedCornerShape(100.dp))
            .clickable {
                annotations = mutableMapOf()
                persist()
                activeTool = null
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text("🧹 Erase All", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE85D4C))
    }
    PsmDownloadPdfButton(title, body)
}

private data class PsmQuestion(val number: String, val question: String, val options: List<String>, val correctAnswer: String, val explanation: String = "")
private fun psmParseQuestions(raw: String): List<PsmQuestion> {
    if (raw.isBlank()) return emptyList()
    val parts = raw.split(Regex("\n(?=\\s*Q?\\d+[.)]\\s)", RegexOption.IGNORE_CASE))
    data class Draft(val question: String, val options: List<String>, val correctAnswer: String, val explanation: String)
    val drafts = parts.map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { block ->
        val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val question = (lines.getOrNull(0) ?: "").replace(Regex("^Q?\\d+[.)]\\s*", RegexOption.IGNORE_CASE), "")
        var correctAnswer = ""
        var explanation = ""
        val cleanOptions = mutableListOf<String>()
        lines.drop(1).forEach { line ->
            val m = Regex("^\\s*(?:ans(?:wer)?|correct\\s*answer)\\s*[:\\-]\\s*([A-D])", RegexOption.IGNORE_CASE).find(line)
            val em = Regex("^Explanation:?\\s*(.*)$", RegexOption.IGNORE_CASE).find(line)
            if (m != null) {
                correctAnswer = m.groupValues[1].uppercase()
            } else if (em != null) {
                explanation = em.groupValues[1]
            } else {
                cleanOptions.add(line)
            }
        }
        // Drop header/title lines (e.g. "1. TOPIC NAME — 50 MCQs") that get
        // mistaken for a question by the numbering split — a real MCQ always
        // has at least 2 options.
        if (cleanOptions.size < 2) null else Draft(question, cleanOptions, correctAnswer, explanation)
    }
    return drafts.mapIndexed { i, d -> PsmQuestion((i + 1).toString(), d.question, d.options, d.correctAnswer, d.explanation) }
}
private fun psmStripMark(opt: String): String = opt.replace(Regex("^[✗✓✔❌×]\\s*"), "").trim()
private fun psmOptionLetter(opt: String, idx: Int): String {
    val m = Regex("^\\(?([A-Da-d])[.)]").find(psmStripMark(opt))
    return if (m != null) m.groupValues[1].uppercase() else ('A' + idx).toString()
}
private fun psmOptionText(opt: String): String = psmStripMark(opt).replace(Regex("^\\(?[A-Da-d][.)]\\s*"), "")

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

// Premium exam-specific MCQ sets (separate from the rotating Daily Practice).
// Firebase path: examMcqSets/{catKey}/{key}  →  raw text in the same
// "1. Question / A) opt / Correct Answer: X / Explanation: ..." format.
private val PSM_EXAM_SETS = listOf(
    Triple("netSet1Raw", "NET — Premium Set 1", 5),
    Triple("tgtSet2Raw", "TGT — Premium Set 2", 5)
)

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
    data class ExamSet(val setKey: String, val label: String) : PsmView()
    object SelfAssessment : PsmView()
}

@Composable
fun PremiumStudyMaterialScreen(catKey: String, catLabel: String, mobile: String, onExit: () -> Unit) {
    var view by remember { mutableStateOf<PsmView>(PsmView.Home) }
    var progressAnalyticsOpen by remember { mutableStateOf(false) }

    if (progressAnalyticsOpen) {
        ProgressAnalyticsScreen(onClose = { progressAnalyticsOpen = false })
        return
    }

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
                    is PsmView.ExamSet -> view = PsmView.Home
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
                onOpenExamSet = { setKey, label -> view = PsmView.ExamSet(setKey, label) },
                onOpenSelfAssessment = { view = PsmView.SelfAssessment },
                onOpenProgressAnalytics = { progressAnalyticsOpen = true }
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
            is PsmView.TopicPointDetail -> PsmTopicPointDetailView(catKey, v.sectionKey, v.pointKey, v.title, v.content)
            is PsmView.DailyPracticeQuiz -> {
                if (v.partKey == "mixedRaw") PsmMixedTestView(catKey, v.partKey)
                else PsmDailySetViewerScreen(catKey, "dailyPractice", v.partKey, v.label)
            }
            is PsmView.ExamSet -> PsmDailySetViewerScreen(catKey, "examMcqSets", v.setKey, v.label)
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
    onOpenExamSet: (String, String) -> Unit,
    onOpenSelfAssessment: () -> Unit,
    onOpenProgressAnalytics: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var syllabus by remember { mutableStateOf("") }
    var writers by remember { mutableStateOf<List<PsmWriterEntry>>(emptyList()) }
    var historyPoints by remember { mutableStateOf<List<PsmTopicPointEntry>>(emptyList()) }
    var sectionCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var dpDate by remember { mutableStateOf("") }
    var dpCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var examSetCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
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
        db.getReference("examMcqSets").child(catKey).get().addOnSuccessListener { s ->
            val counts = mutableMapOf<String, Int>()
            PSM_EXAM_SETS.forEach { (key, _, _) -> counts[key] = psmParseQuestions(s.child(key).getValue(String::class.java) ?: "").size }
            examSetCounts = counts
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

        Text("Premium Exam Sets", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PSM_EXAM_SETS.forEach { (key, label, max) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0xFFFCF3D9), RoundedCornerShape(14.dp))
                        .border(1.5.dp, PSM_GOLD, RoundedCornerShape(14.dp))
                        .clickable { onOpenExamSet(key, label) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                    Text(
                        "${examSetCounts[key] ?: 0} / $max Q ›",
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
                    .clickable(onClick = onOpenProgressAnalytics)
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
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(14.dp))
        if (loading) {
            CircularProgressIndicator(color = PSM_NAVY)
        } else {
            PsmAnnotatableContent(context = androidx.compose.ui.platform.LocalContext.current, contentKey = "${catKey}_${writerKey}_$field", title = title, body = text)
        }
        Spacer(Modifier.height(30.dp))
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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("engteck_prefs", android.content.Context.MODE_PRIVATE) }
    val notesKey = "psm_notes_${catKey}_${writerKey}_$workKey"

    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf("") }
    var characters by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf("") }
    var themes by remember { mutableStateOf("") }
    var questions by remember { mutableStateOf("") }
    var myNotes by remember { mutableStateOf(prefs.getString(notesKey, "") ?: "") }
    var notesSaved by remember { mutableStateOf(false) }
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
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("summary" to "Summary", "characters" to "Characters", "lines" to "Lines").forEach { (key, label) ->
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
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("themes" to "Themes", "questions" to "MCQs", "notes" to "📝 My Notes").forEach { (key, label) ->
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
        } else if (activeTab == "notes") {
            Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                Text(
                    "Ye notes sirf tumhare phone mein save hote hain — koi aur nahi dekh sakta.",
                    fontSize = 11.sp, color = Color(0xFF5B5F6B), modifier = Modifier.padding(bottom = 10.dp)
                )
                OutlinedTextField(
                    value = myNotes,
                    onValueChange = { myNotes = it; notesSaved = false },
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 220.dp),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("Apne notes yahan likho…") }
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        prefs.edit().putString(notesKey, myNotes).apply()
                        notesSaved = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PSM_CORAL),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(if (notesSaved) "Saved ✓" else "Save Notes", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(20.dp))
            }
        } else {
            val text = when (activeTab) {
                "summary" -> summary
                "characters" -> characters
                "lines" -> lines
                "themes" -> themes
                else -> ""
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                PsmAnnotatableContent(
                    context = context,
                    contentKey = "${catKey}_${writerKey}_${workKey}_$activeTab",
                    title = "$title — ${activeTab.replaceFirstChar { it.uppercase() }}",
                    body = text
                )
                Spacer(Modifier.height(30.dp))
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
private fun PsmTopicPointDetailView(catKey: String, sectionKey: String, pointKey: String, title: String, content: String) {
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(14.dp))
        PsmAnnotatableContent(context = androidx.compose.ui.platform.LocalContext.current, contentKey = "${catKey}_${sectionKey}_$pointKey", title = title, body = content)
        Spacer(Modifier.height(30.dp))
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

// ---------- Daily Practice: Theory / Literature set viewer — matches the
// website's "With Answer / Without Answer" viewer with PDF download and
// Answer Key, applied to the 50-question Theory & Literature sets. ----------

@Composable
private fun PsmDailySetViewerScreen(catKey: String, firebaseRoot: String, partKey: String, label: String) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<PsmQuestion>>(emptyList()) }
    var dateStr by remember { mutableStateOf("") }
    var quizMode by remember { mutableStateOf(false) }
    var showAnswerKey by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pickedLetters by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    LaunchedEffect(catKey, partKey) {
        val db = FirebaseDatabase.getInstance().getReference(firebaseRoot).child(catKey)
        db.child(partKey).get().addOnSuccessListener {
            loading = false
            questions = psmParseQuestions(it.getValue(String::class.java) ?: "")
        }.addOnFailureListener { loading = false }
        db.child("date").get().addOnSuccessListener { dateStr = it.getValue(String::class.java) ?: "" }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFAF8F3)).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
            if (dateStr.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Today's Test — $dateStr", fontSize = 13.sp, color = Color(0xFF5B5F6B))
            }
        }

        if (loading) {
            Box(Modifier.padding(horizontal = 20.dp)) { CircularProgressIndicator(color = PSM_NAVY) }
        } else if (questions.isEmpty()) {
            Text("Coming soon.", fontSize = 13.sp, color = Color(0xFF5B5F6B), modifier = Modifier.padding(horizontal = 20.dp))
        } else {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                            .background(if (!quizMode) PSM_GOLD else Color(0xFFF5F3EC))
                            .clickable { quizMode = false }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("✅ With Answer", color = if (!quizMode) PSM_NAVY else Color(0xFF5B5F6B), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                            .background(if (quizMode) PSM_GOLD else Color(0xFFF5F3EC))
                            .clickable { quizMode = true }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("🎯 Without Answer", color = if (quizMode) PSM_NAVY else Color(0xFF5B5F6B), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }

                PsmDownloadMcqPdfButton(label, questions)
                Spacer(Modifier.height(10.dp))

                val answerKey = questions.mapNotNull { q -> if (q.correctAnswer.isNotBlank()) q.number to q.correctAnswer else null }
                if (answerKey.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(PSM_GREEN, RoundedCornerShape(12.dp))
                            .clickable { showAnswerKey = !showAnswerKey }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (showAnswerKey) "▲ Hide Answer Key" else "✅ Show Answer Key", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    if (showAnswerKey) {
                        Spacer(Modifier.height(10.dp))
                        Column {
                            answerKey.chunked(5).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                    rowItems.forEach { (num, ans) ->
                                        Box(
                                            modifier = Modifier.weight(1f).background(Color(0xFFF5F3EC), RoundedCornerShape(8.dp)).padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) { Text("$num: $ans", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PSM_NAVY) }
                                    }
                                    repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            questions.forEachIndexed { idx, q ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(2.dp, PSM_GOLD, RoundedCornerShape(16.dp))
                        .padding(vertical = 14.dp, horizontal = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.size(26.dp).background(PSM_NAVY, CircleShape), contentAlignment = Alignment.Center) {
                            Text(q.number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(q.question, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!quizMode) {
                        Column(modifier = Modifier.padding(start = 36.dp)) {
                            q.options.forEachIndexed { oi, opt ->
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .background(Color(0xFFFAF8F3), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) { Text("${psmOptionLetter(opt, oi)}) ${psmOptionText(opt)}", fontSize = 13.5.sp, color = Color(0xFF1A1A1A)) }
                            }
                            if (q.correctAnswer.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFFEAF6E9), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) { Text("✓ Correct Answer: ${q.correctAnswer}", color = PSM_GREEN, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.padding(start = 36.dp)) {
                            val isRevealed = revealed.contains(idx)
                            q.options.forEachIndexed { oi, opt ->
                                val letter = psmOptionLetter(opt, oi)
                                val isCorrect = letter == q.correctAnswer.trim()
                                val picked = pickedLetters[idx] == letter
                                val bg = when {
                                    !isRevealed -> Color(0xFFFAF8F3)
                                    isCorrect -> Color(0xFFDCF5E0)
                                    picked && !isCorrect -> Color(0xFFFBE0DE)
                                    else -> Color(0xFFFAF8F3)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .background(bg, RoundedCornerShape(10.dp))
                                        .clickable(enabled = !isRevealed) {
                                            pickedLetters = pickedLetters + (idx to letter)
                                            revealed = revealed + idx
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("$letter) ${psmOptionText(opt)}", fontSize = 13.5.sp, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                                    // Pills only appear once this question is revealed (i.e. the
                                    // student tapped an option) — green always marks the correct
                                    // one, red only marks the student's own wrong pick.
                                    if (isRevealed && isCorrect) {
                                        Box(
                                            modifier = Modifier.background(PSM_GREEN, RoundedCornerShape(100.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) { Text("Correct", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                    } else if (isRevealed && picked && !isCorrect) {
                                        Box(
                                            modifier = Modifier.background(Color(0xFFE8544A), RoundedCornerShape(100.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) { Text("Wrong", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                            if (isRevealed && q.explanation.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE7ECF6), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Column {
                                        Text("Solid Fact / Explanation", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = PSM_NAVY)
                                        Spacer(Modifier.height(3.dp))
                                        Text(q.explanation, fontSize = 12.5.sp, color = Color(0xFF1A1A1A))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}


// ---------- Daily Practice: Mixed 125-Q — full scored test, matching the
// website's renderMixedTestMode / renderMixedTestResult exactly (start card
// -> all Q on one page -> submit -> donut-chart result + Answer Review). ----------

@Composable
private fun PsmMixedTestView(catKey: String, partKey: String) {
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<PsmQuestion>>(emptyList()) }
    var dateStr by remember { mutableStateOf("") }
    var testStarted by remember { mutableStateOf(false) }
    var testSubmitted by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    LaunchedEffect(catKey, partKey) {
        val db = FirebaseDatabase.getInstance().getReference("dailyPractice").child(catKey)
        db.child(partKey).get().addOnSuccessListener {
            loading = false
            questions = psmParseQuestions(it.getValue(String::class.java) ?: "")
        }.addOnFailureListener { loading = false }
        db.child("date").get().addOnSuccessListener { dateStr = it.getValue(String::class.java) ?: "" }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PSM_NAVY) }
        return
    }
    val total = questions.size

    Column(Modifier.fillMaxSize().background(Color(0xFFFAF8F3)).padding(20.dp)) {
        if (!(testSubmitted && showResult)) {
            Text("Mixed — All Topics ($total Q)", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
            if (dateStr.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Today's Test — $dateStr", fontSize = 13.sp, color = Color(0xFF5B5F6B))
            }
            Spacer(Modifier.height(16.dp))
        }

        when {
            total == 0 -> Text("Coming soon.", fontSize = 13.sp, color = Color(0xFF5B5F6B))

            !testStarted -> {
                Column(
                    modifier = Modifier.fillMaxWidth().background(PSM_NAVY, RoundedCornerShape(18.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎯", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Full Test — $total Questions", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "All questions will appear on a single page — answer as many as you like, then Submit at the end to see your result.",
                        color = Color(0xFFB9BDC7), fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { testStarted = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PSM_CORAL),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("▶ Start Test", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }

            testSubmitted && !showResult -> {
                Column(
                    modifier = Modifier.fillMaxWidth().background(PSM_NAVY, RoundedCornerShape(18.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(56.dp).background(PSM_GREEN, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Test Submitted!", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap the button below to see your result.", color = Color(0xFFB9BDC7), fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { showResult = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PSM_CORAL),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("📊 Show My Result", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }

            testSubmitted && showResult -> {
                PsmMixedTestResultView(questions, answers, onRetake = {
                    answers = emptyMap(); testSubmitted = false; showResult = false; testStarted = true
                })
            }

            else -> {
                val answeredCount = answers.size
                val progressPct = if (total > 0) answeredCount / total.toFloat() else 0f
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    LinearProgressIndicator(
                        progress = { progressPct }, color = PSM_GOLD, trackColor = Color(0xFFE3DFD3),
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Answered $answeredCount / $total", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                    Spacer(Modifier.height(14.dp))

                    questions.forEachIndexed { idx, q ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                                .background(Color.White, RoundedCornerShape(14.dp))
                                .border(1.dp, PSM_GOLD, RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {
                            Text("${idx + 1}. ${q.question}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A1A))
                            Spacer(Modifier.height(10.dp))
                            q.options.forEachIndexed { oi, opt ->
                                val letter = psmOptionLetter(opt, oi)
                                val picked = answers[idx] == letter
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .background(if (picked) Color(0xFFE8F0FC) else Color(0xFFFAF8F3), RoundedCornerShape(10.dp))
                                        .border(if (picked) 1.5.dp else 0.dp, Color(0xFF2F6FE0), RoundedCornerShape(10.dp))
                                        .clickable { answers = answers + (idx to letter) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) { Text("${psmOptionLetter(opt, oi)}) ${psmOptionText(opt)}", fontSize = 13.5.sp, color = if (picked) Color(0xFF2F6FE0) else Color(0xFF1A1A1A)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { testSubmitted = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PSM_CORAL),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("✅ Submit Test", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PsmMixedTestResultView(questions: List<PsmQuestion>, answers: Map<Int, String>, onRetake: () -> Unit) {
    val total = questions.size
    var attempted = 0
    var right = 0
    var wrong = 0
    questions.forEachIndexed { idx, q ->
        val picked = answers[idx]
        if (picked != null) {
            attempted++
            if (picked == q.correctAnswer.trim()) right++ else wrong++
        }
    }
    val notAttempted = total - attempted
    val pct = if (total > 0) (right * 100 / total) else 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Mixed — All Topics — Result", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth().background(PSM_NAVY, RoundedCornerShape(18.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PsmResultDonut(right = right, wrong = wrong, notAttempted = notAttempted, total = total, pct = pct)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PsmLegendDot(Color(0xFF1F9D55), "Right")
                PsmLegendDot(PSM_RED, "Wrong")
                PsmLegendDot(PSM_GOLD, "Not Attempted")
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PsmStatBox("$total", "TOTAL")
                PsmStatBox("$attempted", "ATTEMPTED")
                PsmStatBox("$notAttempted", "LEFT")
                PsmStatBox("$right", "RIGHT", Color(0xFF1F9D55))
                PsmStatBox("$wrong", "WRONG", PSM_RED)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(100.dp))
                    .clickable { }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("OK", color = PSM_CORAL, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF3A4A66), RoundedCornerShape(100.dp))
                    .clickable(onClick = onRetake).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("↻ Retake Test", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }

        Spacer(Modifier.height(22.dp))
        Text("📋 Answer Review", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(12.dp))

        questions.forEachIndexed { idx, q ->
            val picked = answers[idx]
            val correct = q.correctAnswer.trim()
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.dp, PSM_GOLD, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Text("${idx + 1}. ${q.question}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(10.dp))
                q.options.forEachIndexed { oi, opt ->
                    val letter = psmOptionLetter(opt, oi)
                    val isCorrect = letter == correct
                    val isPicked = picked == letter
                    val bg = when {
                        isPicked && isCorrect -> Color(0xFFDCF5E0)
                        isPicked && !isCorrect -> Color(0xFFFBE0DE)
                        isCorrect -> Color(0xFFDCF5E0)
                        else -> Color(0xFFFAF8F3)
                    }
                    val textColor = when {
                        isPicked && !isCorrect -> PSM_RED
                        isCorrect -> Color(0xFF1F7A3D)
                        else -> Color(0xFF1A1A1A)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(bg, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) { Text("$letter) ${psmOptionText(opt)}", fontSize = 13.5.sp, color = textColor, fontWeight = if (isCorrect || isPicked) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun PsmResultDonut(right: Int, wrong: Int, notAttempted: Int, total: Int, pct: Int) {
    val strokeWidthDp = 14.dp
    val sizeDp = 120.dp
    androidx.compose.foundation.Canvas(modifier = Modifier.size(sizeDp)) {
        val stroke = strokeWidthDp.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
        val safeTotal = if (total > 0) total else 1
        val rightSweep = 360f * right / safeTotal
        val wrongSweep = 360f * wrong / safeTotal
        val notAttemptedSweep = 360f - rightSweep - wrongSweep

        var startAngle = -90f
        drawArc(color = Color(0xFF1F9D55), startAngle = startAngle, sweepAngle = rightSweep, useCenter = false, topLeft = topLeft, size = arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        startAngle += rightSweep
        drawArc(color = Color(0xFFE85D4C), startAngle = startAngle, sweepAngle = wrongSweep, useCenter = false, topLeft = topLeft, size = arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        startAngle += wrongSweep
        drawArc(color = Color(0xFFD4A017), startAngle = startAngle, sweepAngle = notAttemptedSweep, useCenter = false, topLeft = topLeft, size = arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
    }
    Box(modifier = Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Text("$pct%", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun PsmLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color(0xFFB9BDC7), fontSize = 12.sp)
    }
}

@Composable
private fun RowScope.PsmStatBox(value: String, label: String, valueColor: Color = Color.White) {
    Column(
        modifier = Modifier.weight(1f).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = valueColor, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color(0xFF9099AD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

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

@Composable
private fun JustifiedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    lineSpacing: Int = 8
) {
    val words = remember(text) { text.split(" ").filter { it.isNotEmpty() } }
    Layout(
        modifier = modifier,
        content = {
            words.forEach { w ->
                Text(w, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, softWrap = false)
            }
            Text(" ", color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, softWrap = false)
        }
    ) { measurables, constraints ->
        val maxWidth = constraints.maxWidth
        val loose = Constraints()
        val wordPlaceables = measurables.dropLast(1).map { it.measure(loose) }
        val spaceWidth = measurables.last().measure(loose).width

        data class Line(val items: MutableList<androidx.compose.ui.layout.Placeable>, var width: Int)
        val lines = mutableListOf<Line>()
        var current = Line(mutableListOf(), 0)
        wordPlaceables.forEach { p ->
            val newWidth = if (current.items.isEmpty()) p.width else current.width + spaceWidth + p.width
            if (current.items.isNotEmpty() && newWidth > maxWidth) {
                lines.add(current)
                current = Line(mutableListOf(p), p.width)
            } else {
                current.items.add(p)
                current.width = newWidth
            }
        }
        if (current.items.isNotEmpty()) lines.add(current)

        val lineHeight = (wordPlaceables.firstOrNull()?.height ?: 0) + lineSpacing
        val totalHeight = if (lines.isEmpty()) 0 else lines.size * lineHeight

        layout(maxWidth, totalHeight) {
            lines.forEachIndexed { lineIndex, line ->
                val isLastLine = lineIndex == lines.size - 1
                val y = lineIndex * lineHeight
                if (isLastLine || line.items.size <= 1) {
                    var x = 0
                    line.items.forEach { p ->
                        p.placeRelative(x, y)
                        x += p.width + spaceWidth
                    }
                } else {
                    val wordsWidth = line.items.sumOf { it.width }
                    val gapCount = line.items.size - 1
                    val totalGap = maxWidth - wordsWidth
                    val gap = if (totalGap > 0) totalGap / gapCount else spaceWidth
                    var x = 0
                    line.items.forEachIndexed { idx, p ->
                        p.placeRelative(x, y)
                        x += p.width + gap
                    }
                }
            }
        }
    }
}

package com.shreeyog.engteck.screens

import android.content.ContentValues
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream

private data class PremiumQuestion(
    val number: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String = ""
)

private fun parsePremiumQuestions(raw: String): List<PremiumQuestion> {
    if (raw.isBlank()) return emptyList()
    val parts = raw.split(Regex("\n(?=\\s*\\d+[.)]\\s)"))
    return parts.map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { i, block ->
        val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val question = (lines.getOrNull(0) ?: "").replace(Regex("^\\d+[.)]\\s*"), "")
        var correctAnswer = ""
        var explanation = ""
        val cleanOptions = mutableListOf<String>()
        var j = 1
        while (j < lines.size) {
            val line = lines[j]
            val m = Regex("^Correct Answer:\\s*([A-D])", RegexOption.IGNORE_CASE).find(line)
            when {
                m != null -> correctAnswer = m.groupValues[1].uppercase()
                line.startsWith("Explanation:", ignoreCase = true) -> {
                    val sb = StringBuilder(line.substringAfter(":").trim())
                    var k = j + 1
                    while (k < lines.size) {
                        sb.append(" ").append(lines[k])
                        k++
                    }
                    explanation = sb.toString().trim()
                    j = lines.size
                }
                else -> cleanOptions.add(line)
            }
            j++
        }
        PremiumQuestion((i + 1).toString(), question, cleanOptions, correctAnswer, explanation)
    }
}
private fun premiumOptionLetter(opt: String, idx: Int): String {
    val m = Regex("^\\(?([A-Da-d])[.)]").find(opt)
    return if (m != null) m.groupValues[1].uppercase() else ('A' + idx).toString()
}
private fun premiumOptionText(opt: String): String = opt.replace(Regex("^\\(?[A-Da-d][.)]\\s*"), "")

private fun wrapPremiumText(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isEmpty()) return listOf("")
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
        val test = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(test) > maxWidth && current.isNotEmpty()) {
            lines.add(current.toString())
            current = StringBuilder(word)
        } else {
            current = StringBuilder(test)
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
}

// ============================================================================
// PDF generation — matches the "Colourful MCQ" style used across the app
// (rounded option boxes, green highlight for the correct answer, dark navy
// "SOLID FACT / EXPLANATION" box, diagonal watermark, gold header band,
// "Page X of Y" footer). Uses the same two-pass approach as psmSaveMcqPdf:
// a dry run computes totalPages first, then the real draw pass breaks pages
// at the exact same spots — so the footer page count is always correct.
// ============================================================================
private fun buildPremiumPdfDocument(title: String, questions: List<PremiumQuestion>): PdfDocument {
    val pageWidth = 595
    val pageHeight = 842
    val margin = 30f
    val contentWidth = pageWidth - margin * 2 - 20f
    val lineHeight = 14.5f
    val document = PdfDocument()

    val titlePaint = Paint().apply { color = AColor.WHITE; textSize = 17f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    val subtitlePaint = Paint().apply { color = AColor.WHITE; textSize = 10.5f; textAlign = Paint.Align.CENTER }
    val bandPaint = Paint().apply { color = AColor.rgb(0x12, 0x20, 0x3D) }
    val qPaint = Paint().apply { color = AColor.rgb(0x1A, 0x1A, 0x1A); textSize = 12.5f; isFakeBoldText = true }
    val optPaint = Paint().apply { color = AColor.rgb(0x1A, 0x1A, 0x1A); textSize = 11.5f }
    val correctPaint = Paint().apply { color = AColor.rgb(0x1F, 0x7A, 0x3D); textSize = 11.5f; isFakeBoldText = true }
    val correctBoxPaint = Paint().apply { color = AColor.rgb(0xEA, 0xF6, 0xE9) }
    val correctBorderPaint = Paint().apply { color = AColor.rgb(0x2E, 0x9B, 0x53); style = Paint.Style.STROKE; strokeWidth = 2f }
    val normalBoxPaint = Paint().apply { color = AColor.rgb(0xFA, 0xF8, 0xF3) }
    val normalBorderPaint = Paint().apply { color = AColor.rgb(0xD9, 0xD3, 0xC4); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    val explLabelPaint = Paint().apply { color = AColor.rgb(0xD4, 0xA0, 0x17); textSize = 10f; isFakeBoldText = true }
    val explBodyPaint = Paint().apply { color = AColor.WHITE; textSize = 11f }
    val explBoxPaint = Paint().apply { color = AColor.rgb(0x12, 0x20, 0x3D) }
    val footerRightPaint = Paint().apply { color = AColor.rgb(0x8A, 0x8A, 0x8A); textSize = 9.5f; textAlign = Paint.Align.RIGHT }
    val watermarkPaint = Paint().apply { color = AColor.argb(14, 0, 0, 0); textSize = 40f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }

    fun blockNeeds(q: PremiumQuestion): List<Float> {
        val needs = mutableListOf<Float>()
        val qLines = wrapPremiumText("${q.number}. ${q.question}", qPaint, contentWidth)
        needs.add(qLines.size * lineHeight + 10f)
        q.options.forEachIndexed { oi, opt ->
            val letter = premiumOptionLetter(opt, oi)
            val text = premiumOptionText(opt)
            val optLines = wrapPremiumText("$letter) $text", optPaint, contentWidth - 20f)
            needs.add(optLines.size * lineHeight + 16f + 4f)
        }
        if (q.explanation.isNotBlank()) {
            val explLines = wrapPremiumText(q.explanation, explBodyPaint, contentWidth - 20f)
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
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas

    fun drawWatermark(cv: Canvas) {
        cv.save()
        cv.rotate(-28f, pageWidth / 2f, pageHeight / 2f)
        for (row in -2..4) {
            cv.drawText("Shree English Classes", pageWidth / 2f, pageHeight / 2f + row * 140f, watermarkPaint)
        }
        cv.restore()
    }
    fun drawFooter(cv: Canvas) {
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
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            drawWatermark(canvas)
            drawHeader(false)
            y = 46f
        }
    }

    questions.forEach { q ->
        val qLines = wrapPremiumText("${q.number}. ${q.question}", qPaint, contentWidth)
        val qNeed = qLines.size * lineHeight + 10f
        ensureSpace(qNeed)
        qLines.forEachIndexed { i, line -> canvas.drawText(line, margin, y + (i * lineHeight), qPaint) }
        y += qNeed

        q.options.forEachIndexed { oi, opt ->
            val letter = premiumOptionLetter(opt, oi)
            val text = premiumOptionText(opt)
            val isCorrect = letter == q.correctAnswer.trim()
            val optLines = wrapPremiumText("$letter) $text", optPaint, contentWidth - 20f)
            val boxHeight = optLines.size * lineHeight + 16f
            val optNeed = boxHeight + 4f
            ensureSpace(optNeed)
            val boxTop = y - 14f
            val boxPaint = if (isCorrect) correctBoxPaint else normalBoxPaint
            val borderPaint = if (isCorrect) correctBorderPaint else normalBorderPaint
            val boxRect = RectF(margin + 14f, boxTop, pageWidth - margin, boxTop + boxHeight)
            canvas.drawRoundRect(boxRect, 6f, 6f, boxPaint)
            canvas.drawRoundRect(boxRect, 6f, 6f, borderPaint)
            val txtPaint = if (isCorrect) correctPaint else optPaint
            optLines.forEachIndexed { i, line -> canvas.drawText(line, margin + 22f, y + (i * lineHeight), txtPaint) }
            y += optNeed
        }

        if (q.explanation.isNotBlank()) {
            val explLines = wrapPremiumText(q.explanation, explBodyPaint, contentWidth - 20f)
            val boxHeight = (explLines.size + 1) * lineHeight + 8f
            val explNeed = boxHeight + 6f
            ensureSpace(explNeed)
            canvas.drawRoundRect(RectF(margin + 14f, y - 10f, pageWidth - margin, y - 10f + boxHeight), 6f, 6f, explBoxPaint)
            canvas.drawText("SOLID FACT / EXPLANATION", margin + 22f, y, explLabelPaint)
            explLines.forEachIndexed { i, line -> canvas.drawText(line, margin + 22f, y + lineHeight + (i * lineHeight), explBodyPaint) }
            y += explNeed
        }
        y += 12f
    }
    drawFooter(canvas)
    document.finishPage(page)
    return document
}

private fun savePremiumPdfToDownloads(context: android.content.Context, title: String, questions: List<PremiumQuestion>): String? {
    return try {
        val document = buildPremiumPdfDocument(title, questions)
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "$safeTitle.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> document.writeTo(out) }
                document.close()
                "Downloads/$fileName"
            } else {
                document.close(); null
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }
}

// Manual justify — native TextAlign.Justify doesn't render reliably on all devices/OS
// versions, so this measures each word itself and stretches inter-word gaps to fill the
// full line width (except the last line, which stays left-aligned).
@Composable
private fun JustifiedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    lineSpacing: Int = 6
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

@Composable
fun PremiumSetReaderScreen(catKey: String, setKey: String, setTitle: String, onBack: () -> Unit) {
    val context = LocalContext.current
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(questions) { q ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(16.dp))
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF12203D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(q.number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            JustifiedText(
                                q.question,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                            val userSelected = selectedAnswers[q.number]
                            val revealed = showAnswers || userSelected != null

                            q.options.forEachIndexed { optIdx, opt ->
                                val optLetter = premiumOptionLetter(opt, optIdx)
                                val correctLetter = q.correctAnswer.trim()
                                val isSelected = userSelected == optLetter
                                val isCorrectOption = optLetter == correctLetter

                                val bgColor = when {
                                    showAnswers && isCorrectOption -> Color(0xFFDCF5E0)
                                    showAnswers -> Color.Transparent
                                    userSelected == null -> Color.Transparent
                                    isSelected && isCorrectOption -> Color(0xFFDCF5E0)
                                    isSelected && !isCorrectOption -> Color(0xFFFBE0DE)
                                    isCorrectOption -> Color(0xFFDCF5E0)
                                    else -> Color.Transparent
                                }
                                val textColor = when {
                                    showAnswers && isCorrectOption -> Color(0xFF1F7A3D)
                                    showAnswers -> Color(0xFF5B5F6B)
                                    userSelected == null -> Color(0xFF5B5F6B)
                                    isSelected && isCorrectOption -> Color(0xFF1F7A3D)
                                    isSelected && !isCorrectOption -> Color(0xFFC0392B)
                                    isCorrectOption -> Color(0xFF1F7A3D)
                                    else -> Color(0xFF5B5F6B)
                                }
                                val borderColor = when {
                                    showAnswers && isCorrectOption -> Color(0xFF1F7A3D)
                                    showAnswers -> Color(0xFFF0EEE7)
                                    bgColor == Color.Transparent -> Color(0xFFE3DFD3)
                                    else -> textColor
                                }
                                val boxBg = if (bgColor == Color.Transparent) Color.White else bgColor

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
                                    JustifiedText(
                                        if (showAnswers && isCorrectOption) "✓ $optLetter) ${premiumOptionText(opt)}" else "$optLetter) ${premiumOptionText(opt)}",
                                        fontSize = 14.sp,
                                        color = textColor,
                                        fontWeight = if (bgColor != Color.Transparent) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (revealed && q.correctAnswer.isNotEmpty()) {
                                Text(
                                    "Correct Answer: ${q.correctAnswer}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1F7A3D),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp, bottom = if (q.explanation.isNotEmpty()) 4.dp else 0.dp)
                                )
                            }
                            if (revealed && q.explanation.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .background(Color(0xFF12203D), RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(8.dp)
                                            .background(Color(0xFF4CAF50), CircleShape)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Explanation:",
                                            color = Color(0xFFF0E6C8),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        JustifiedText(
                                            q.explanation,
                                            color = Color(0xFFF0E6C8),
                                            fontSize = 13.sp,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(10.dp)) }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE85D4C))
                            .clickable {
                                val path = savePremiumPdfToDownloads(context, setTitle, questions)
                                if (path != null) {
                                    Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↓ Download PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

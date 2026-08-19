package com.shreeyog.engteck.screens

import android.content.ContentValues
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
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

// Justified line drawing — stretches inter-word spaces to fill maxWidth. Last line of a
// paragraph is left-aligned (standard typographic rule).
private fun drawPremiumJustified(
    canvas: Canvas,
    line: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    paint: Paint,
    isLastLine: Boolean
) {
    val words = line.split(" ").filter { it.isNotEmpty() }
    if (isLastLine || words.size <= 1) {
        canvas.drawText(line, x, y, paint)
        return
    }
    val textWidthNoSpaces = words.sumOf { paint.measureText(it).toDouble() }.toFloat()
    val gapCount = words.size - 1
    val totalGapWidth = maxWidth - textWidthNoSpaces
    val gapWidth = if (totalGapWidth > 0) totalGapWidth / gapCount else paint.measureText(" ")
    var cx = x
    words.forEachIndexed { idx, word ->
        canvas.drawText(word, cx, y, paint)
        cx += paint.measureText(word)
        if (idx < words.size - 1) cx += gapWidth
    }
}

private fun buildPremiumPdfDocument(title: String, questions: List<PremiumQuestion>): PdfDocument {
    val pageWidth = 595
    val pageHeight = 842
    val leftMargin = 24f
    val rightMargin = 24f
    val contentWidth = pageWidth - leftMargin - rightMargin
    val document = PdfDocument()

    val titlePaint = Paint().apply { color = AColor.WHITE; textSize = 20f; isFakeBoldText = true }
    val subtitlePaint = Paint().apply { color = AColor.argb(200, 255, 255, 255); textSize = 11f }
    val bandPaint = Paint().apply { color = AColor.rgb(0x12, 0x20, 0x3D) }
    val goldPaint = Paint().apply { color = AColor.rgb(0xD4, 0xA0, 0x17) }
    val cardBgEven = Paint().apply { color = AColor.WHITE }
    val cardBgOdd = Paint().apply { color = AColor.rgb(0xF5, 0xF3, 0xEC) }
    val dividerPaint = Paint().apply { color = AColor.rgb(0xD4, 0xA0, 0x17); strokeWidth = 1.5f }
    val borderPaint = Paint().apply {
        color = AColor.rgb(0xD4, 0xA0, 0x17)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val circlePaint = Paint().apply { color = AColor.rgb(0x12, 0x20, 0x3D) }
    val circleTextPaint = Paint().apply { color = AColor.WHITE; textSize = 11f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    val qPaint = Paint().apply { color = AColor.rgb(0x1A, 0x1A, 0x1A); textSize = 13f; isFakeBoldText = true }
    val optGreenPaint = Paint().apply { color = AColor.rgb(0x1F, 0x7A, 0x3D); textSize = 12f; isFakeBoldText = true }
    val optRedPaint = Paint().apply { color = AColor.rgb(0xC0, 0x39, 0x2B); textSize = 12f }
    val correctAnswerPaint = Paint().apply { color = AColor.rgb(0x1F, 0x7A, 0x3D); textSize = 12.5f; isFakeBoldText = true }
    val explBgPaint = Paint().apply { color = AColor.rgb(0x12, 0x20, 0x3D) }
    val explHeaderPaint = Paint().apply { color = AColor.rgb(0xF0, 0xE6, 0xC8); textSize = 12.5f; isFakeBoldText = true }
    val explBodyPaint = Paint().apply { color = AColor.rgb(0xF0, 0xE6, 0xC8); textSize = 11.5f }
    val dotPaint = Paint().apply { color = AColor.rgb(0x4C, 0xAF, 0x50) }

    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas

    fun drawHeader() {
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 64f, bandPaint)
        canvas.drawRect(0f, 60f, pageWidth.toFloat(), 64f, goldPaint)
        canvas.drawText(title, leftMargin, 32f, titlePaint)
        canvas.drawText("Shree English Classes — Premium", leftMargin, 50f, subtitlePaint)
    }
    fun drawPageBorder() {
        canvas.drawRect(4f, 4f, pageWidth - 4f, pageHeight - 4f, borderPaint)
    }

    drawHeader()
    var y = 64f
    val marginBottom = 790f
    val textStartX = leftMargin + 34f
    val textWidth = contentWidth - 34f
    val explInnerPad = 10f
    val explTextWidth = contentWidth - (explInnerPad * 2f)

    questions.forEachIndexed { index, q ->
        val qLines = wrapPremiumText("${q.number}. ${q.question}", qPaint, textWidth)
        val optLinesList = q.options.mapIndexed { idx, opt ->
            val letter = premiumOptionLetter(opt, idx)
            val isCorrect = letter == q.correctAnswer.trim()
            val prefix = if (isCorrect) "✓ " else "✗ "
            wrapPremiumText("$prefix$letter) ${premiumOptionText(opt)}", if (isCorrect) optGreenPaint else optRedPaint, textWidth - 10f)
        }
        val explLines = if (q.explanation.isNotEmpty()) wrapPremiumText(q.explanation, explBodyPaint, explTextWidth) else emptyList()

        var blockHeight = 20f + (qLines.size * 16f)
        optLinesList.forEach { blockHeight += it.size * 15f }
        if (q.correctAnswer.isNotEmpty()) blockHeight += 20f
        if (explLines.isNotEmpty()) blockHeight += 8f + 18f + (explLines.size * 14f) + 12f
        blockHeight += 12f

        if (y + blockHeight > marginBottom) {
            drawPageBorder()
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 24f
        }

        val cardTop = y
        val bandPaintToUse = if (index % 2 == 0) cardBgEven else cardBgOdd
        canvas.drawRect(0f, cardTop, pageWidth.toFloat(), cardTop + blockHeight, bandPaintToUse)

        val circleCy = cardTop + 22f
        canvas.drawCircle(leftMargin + 11f, circleCy, 11f, circlePaint)
        canvas.drawText(q.number, leftMargin + 11f, circleCy + 4f, circleTextPaint)

        var lineY = cardTop + 20f
        qLines.forEachIndexed { idx, line ->
            drawPremiumJustified(canvas, line, textStartX, lineY, textWidth, qPaint, idx == qLines.size - 1)
            lineY += 16f
        }
        lineY += 4f

        val correctLetter = q.correctAnswer.trim()
        q.options.forEachIndexed { idx, opt ->
            val letter = premiumOptionLetter(opt, idx)
            val isCorrect = letter == correctLetter
            val paintToUse = if (isCorrect) optGreenPaint else optRedPaint
            val optLines = optLinesList[idx]
            optLines.forEachIndexed { li, line ->
                drawPremiumJustified(canvas, line, textStartX + 10f, lineY, textWidth - 10f, paintToUse, li == optLines.size - 1)
                lineY += 15f
            }
        }

        if (q.correctAnswer.isNotEmpty()) {
            lineY += 4f
            canvas.drawText("Correct Answer: ${q.correctAnswer}", textStartX, lineY, correctAnswerPaint)
            lineY += 16f
        }

        if (explLines.isNotEmpty()) {
            lineY += 4f
            val boxTop = lineY
            val boxHeight = 18f + (explLines.size * 14f) + 10f
            canvas.drawRect(leftMargin, boxTop, pageWidth - rightMargin, boxTop + boxHeight, explBgPaint)
            canvas.drawCircle(leftMargin + explInnerPad + 3f, boxTop + 15f, 3f, dotPaint)
            canvas.drawText("Explanation:", leftMargin + explInnerPad + 12f, boxTop + 18f, explHeaderPaint)
            var ey = boxTop + 34f
            explLines.forEachIndexed { idx, line ->
                drawPremiumJustified(canvas, line, leftMargin + explInnerPad, ey, explTextWidth, explBodyPaint, idx == explLines.size - 1)
                ey += 14f
            }
        }

        canvas.drawLine(0f, cardTop + blockHeight, pageWidth.toFloat(), cardTop + blockHeight, dividerPaint)
        y = cardTop + blockHeight
    }
    drawPageBorder()
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

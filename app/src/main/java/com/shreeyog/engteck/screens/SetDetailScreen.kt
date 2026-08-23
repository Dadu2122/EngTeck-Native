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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream

data class ParsedQuestion(
    val number: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String = ""
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
        var explanation = ""

        var i = 1
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("Correct Answer:") -> {
                    correctAnswer = line.removePrefix("Correct Answer:").trim()
                }
                line.startsWith("Explanation:") -> {
                    val sb = StringBuilder(line.removePrefix("Explanation:").trim())
                    var j = i + 1
                    while (j < lines.size) {
                        sb.append(" ").append(lines[j])
                        j++
                    }
                    explanation = sb.toString().trim()
                    i = lines.size
                }
                Regex("^[A-D]\\)").containsMatchIn(line) -> options.add(line)
            }
            i++
        }
        ParsedQuestion(number, question, options, correctAnswer, explanation)
    }
}

private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
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

/** Draws one line justified (stretches spaces to fill maxWidth). Last line of a
 *  paragraph should NOT be justified (standard typographic rule) — pass isLastLine=true. */
private fun drawJustifiedLine(
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

private fun buildPdfDocument(title: String, questions: List<ParsedQuestion>): PdfDocument {
    val pageWidth = 595
    val pageHeight = 842
    val leftMargin = 30f
    val rightMargin = 30f
    val contentWidth = pageWidth - leftMargin - rightMargin
    val document = PdfDocument()

    // Colors — matches the "Shree English Classes" reference PDFs exactly:
    // navy header band + gold divider, pill-style option boxes (green = correct),
    // navy "SOLID FACT / EXPLANATION" box, diagonal watermark, page footer.
    val navy = AColor.rgb(0x12, 0x20, 0x3D)
    val gold = AColor.rgb(0xD4, 0xA0, 0x17)
    val black = AColor.rgb(0x1A, 0x1A, 0x1A)
    val greenTxt = AColor.rgb(0x1F, 0x7A, 0x3D)
    val greenBg = AColor.rgb(0xDC, 0xF5, 0xE0)
    val greenBorder = AColor.rgb(0x2E, 0x9B, 0x53)
    val grayBorder = AColor.rgb(0xD9, 0xD3, 0xC4)
    val grayTxt = AColor.rgb(0x8A, 0x8A, 0x8A)
    val cream = AColor.rgb(0xF0, 0xE6, 0xC8)

    val titlePaint = Paint().apply { color = AColor.WHITE; textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    val subtitlePaint = Paint().apply { color = gold; textSize = 10.5f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    val qPaint = Paint().apply { color = navy; textSize = 12.5f; isFakeBoldText = true }
    val optLetterPaint = Paint().apply { color = navy; textSize = 11f; isFakeBoldText = true }
    val optLetterCorrectPaint = Paint().apply { color = greenTxt; textSize = 11f; isFakeBoldText = true }
    val optTextPaint = Paint().apply { color = black; textSize = 11f }
    val optTextCorrectPaint = Paint().apply { color = greenTxt; textSize = 11f }
    val boxBorderPaint = Paint().apply { color = grayBorder; style = Paint.Style.STROKE; strokeWidth = 1.2f }
    val boxBorderCorrectPaint = Paint().apply { color = greenBorder; style = Paint.Style.STROKE; strokeWidth = 1.4f }
    val boxFillPaint = Paint().apply { color = AColor.WHITE; style = Paint.Style.FILL }
    val boxFillCorrectPaint = Paint().apply { color = greenBg; style = Paint.Style.FILL }
    val headerBandPaint = Paint().apply { color = navy }
    val goldLinePaint = Paint().apply { color = gold }
    val explBgPaint = Paint().apply { color = navy; style = Paint.Style.FILL }
    val explLabelPaint = Paint().apply { color = gold; textSize = 9.5f; isFakeBoldText = true }
    val explBodyPaint = Paint().apply { color = cream; textSize = 10.5f }
    val footerPaint = Paint().apply { color = grayTxt; textSize = 9.5f; textAlign = Paint.Align.RIGHT }
    val watermarkPaint = Paint().apply { color = AColor.argb(14, 0, 0, 0); textSize = 42f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }

    val headerH = 78f
    val goldLineH = 4f
    val marginBottomLimit = pageHeight - 60f
    val footerY = pageHeight - 30f

    fun optionLines(text: String, paint: Paint) = wrapText(text, paint, contentWidth - 50f)

    fun blockHeight(q: ParsedQuestion): Float {
        val qLines = wrapText("${q.number}.  ${q.question}", qPaint, contentWidth)
        var h = qLines.size * 16f + 10f
        q.options.forEach { opt ->
            val text = opt.substringAfter(")").trim()
            val lines = optionLines(text, optTextPaint)
            val boxH = maxOf(38f, lines.size * 14f + 22f)
            h += boxH + 8f
        }
        if (q.explanation.isNotEmpty()) {
            val eLines = wrapText(q.explanation, explBodyPaint, contentWidth - 28f)
            h += 30f + eLines.size * 13f + 16f
        }
        h += 22f
        return h
    }

    // Pass 1 — simulate layout to know how many pages are needed, so the
    // footer can print "Page X of Y" with the correct total.
    val pages = mutableListOf<MutableList<ParsedQuestion>>()
    run {
        var current = mutableListOf<ParsedQuestion>()
        var y = headerH + goldLineH + 26f
        questions.forEach { q ->
            val h = blockHeight(q)
            if (y + h > marginBottomLimit) {
                pages.add(current)
                current = mutableListOf()
                y = 40f
            }
            current.add(q)
            y += h
        }
        if (current.isNotEmpty()) pages.add(current)
    }
    val totalPages = pages.size.coerceAtLeast(1)

    fun drawWatermark(canvas: Canvas) {
        canvas.save()
        canvas.rotate(-28f, pageWidth / 2f, pageHeight / 2f)
        for (row in -2..4) {
            canvas.drawText("Shree English Classes", pageWidth / 2f, pageHeight / 2f + row * 160f, watermarkPaint)
        }
        canvas.restore()
    }

    // Pass 2 — actual drawing.
    pages.forEachIndexed { pageIndex, pageQuestions ->
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
        val canvas = page.canvas
        drawWatermark(canvas)

        var y: Float
        if (pageIndex == 0) {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerH, headerBandPaint)
            canvas.drawRect(0f, headerH, pageWidth.toFloat(), headerH + goldLineH, goldLinePaint)
            canvas.drawText(title, pageWidth / 2f, 38f, titlePaint)
            canvas.drawText("Shree English Classes  •  Green = Correct Answer", pageWidth / 2f, 58f, subtitlePaint)
            y = headerH + goldLineH + 26f
        } else {
            y = 40f
        }

        pageQuestions.forEach { q ->
            val qLines = wrapText("${q.number}.  ${q.question}", qPaint, contentWidth)
            var lineY = y
            qLines.forEachIndexed { idx, line ->
                drawJustifiedLine(canvas, line, leftMargin, lineY, contentWidth, qPaint, idx == qLines.size - 1)
                lineY += 16f
            }
            y = lineY + 6f

            val correctLetter = q.correctAnswer.trim()
            q.options.forEach { opt ->
                val letter = opt.substringBefore(")").trim()
                val text = opt.substringAfter(")").trim()
                val isCorrect = letter == correctLetter
                val lines = optionLines(text, if (isCorrect) optTextCorrectPaint else optTextPaint)
                val boxH = maxOf(38f, lines.size * 14f + 22f)
                val boxTop = y
                val boxBottom = y + boxH
                val rect = android.graphics.RectF(leftMargin, boxTop, leftMargin + contentWidth, boxBottom)
                canvas.drawRoundRect(rect, 8f, 8f, if (isCorrect) boxFillCorrectPaint else boxFillPaint)
                canvas.drawRoundRect(rect, 8f, 8f, if (isCorrect) boxBorderCorrectPaint else boxBorderPaint)
                canvas.drawText("$letter)", leftMargin + 14f, boxTop + 15f, if (isCorrect) optLetterCorrectPaint else optLetterPaint)
                lines.forEachIndexed { li, line ->
                    canvas.drawText(line, leftMargin + 42f, boxTop + 15f + li * 14f, if (isCorrect) optTextCorrectPaint else optTextPaint)
                }
                y = boxBottom + 8f
            }

            if (q.explanation.isNotEmpty()) {
                val eLines = wrapText(q.explanation, explBodyPaint, contentWidth - 28f)
                val boxH = 30f + eLines.size * 13f + 10f
                val boxTop = y
                val boxBottom = y + boxH
                canvas.drawRoundRect(android.graphics.RectF(leftMargin, boxTop, leftMargin + contentWidth, boxBottom), 10f, 10f, explBgPaint)
                canvas.drawText("SOLID FACT / EXPLANATION", leftMargin + 14f, boxTop + 18f, explLabelPaint)
                eLines.forEachIndexed { li, line ->
                    canvas.drawText(line, leftMargin + 14f, boxTop + 36f + li * 13f, explBodyPaint)
                }
                y = boxBottom + 20f
            } else {
                y += 14f
            }
        }

        canvas.drawText("Page ${pageIndex + 1} of $totalPages", pageWidth - rightMargin, footerY, footerPaint)
        document.finishPage(page)
    }
    return document
}

private fun savePdfToDownloads(context: android.content.Context, title: String, questions: List<ParsedQuestion>): String? {
    return try {
        val document = buildPdfDocument(title, questions)
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
                document.close()
                null
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
fun SetDetailScreen(catKey: String, setKey: String, setTitle: String) {
    var loading by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<ParsedQuestion>>(emptyList()) }
    var showAnswers by remember { mutableStateOf(true) }
    var selectedAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val context = LocalContext.current

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12203D))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$setTitle 🔴TEST-V2", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                    .clickable {
                        showAnswers = false
                        selectedAnswers = emptyMap()
                    }
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
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(questions) { q ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
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
                            JustifiedText(
                                q.question,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val userSelected = selectedAnswers[q.number]
                        q.options.forEach { opt ->
                            val optLetter = opt.substringBefore(")").trim()
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
                            val boxBg = if (bgColor == Color.Transparent) Color.White else bgColor

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(boxBg)
                                    .clickable(enabled = !showAnswers && userSelected == null) {
                                        selectedAnswers = selectedAnswers + (q.number to optLetter)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 18.dp)
                            ) {
                                JustifiedText(
                                    if (showAnswers && isCorrectOption) "✓ $opt" else opt,
                                    fontSize = 14.sp,
                                    color = textColor,
                                    fontWeight = if (bgColor != Color.Transparent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            HorizontalDivider(color = Color(0xFFD9D3C4), thickness = 1.5.dp)
                        }

                        val revealed = showAnswers || userSelected != null

                        if (revealed && q.correctAnswer.isNotEmpty()) {
                            Text(
                                "Correct Answer: ${q.correctAnswer}",
                                fontSize = 13.sp,
                                color = Color(0xFF1F7A3D),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = if (q.explanation.isNotEmpty()) 4.dp else 12.dp)
                            )
                        }

                        if (revealed && q.explanation.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 14.dp)
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
                                        "SOLID FACT / EXPLANATION",
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
                        } else if (!revealed) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

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
                        .clickable {
                            val path = savePdfToDownloads(context, setTitle, questions)
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

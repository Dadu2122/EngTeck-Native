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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream

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

private fun buildPdfDocument(title: String, questions: List<ParsedQuestion>): PdfDocument {
    val pageWidth = 595
    val pageHeight = 842
    val document = PdfDocument()
    val titlePaint = Paint().apply { color = AColor.WHITE; textSize = 20f; isFakeBoldText = true }
    val bandPaint = Paint().apply { color = AColor.rgb(0x12, 0x20, 0x3D) }
    val qPaint = Paint().apply { color = AColor.rgb(0x1A, 0x1A, 0x1A); textSize = 13f; isFakeBoldText = true }
    val optPaint = Paint().apply { color = AColor.rgb(0x5B, 0x5F, 0x6B); textSize = 12f }
    val ansPaint = Paint().apply { color = AColor.rgb(0x1F, 0x7A, 0x3D); textSize = 12f; isFakeBoldText = true }

    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 60f, bandPaint)
    canvas.drawText(title, 30f, 38f, titlePaint)
    var y = 90f
    val marginBottom = 800f

    for (q in questions) {
        if (y > marginBottom) {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 40f
        }
        canvas.drawText("${q.number}. ${q.question}", 30f, y, qPaint)
        y += 20f
        q.options.forEach { opt ->
            canvas.drawText(opt, 45f, y, optPaint)
            y += 17f
        }
        if (q.correctAnswer.isNotEmpty()) {
            canvas.drawText("Correct Answer: ${q.correctAnswer}", 45f, y, ansPaint)
            y += 17f
        }
        y += 12f
    }
    document.finishPage(page)
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
            Text(setTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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

package com.shreeyog.engteck.screens

import android.app.Activity
import android.content.ContentValues
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.payment.buildRazorpayCheckoutIntent
import com.shreeyog.engteck.payment.createRazorpayOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val MINIBOOK_FREE_SECTIONS = 5

// A pasted-text block is treated as an MCQ card if its first line looks like "Q1. ..." or "1. ...".
// Declared up top so both the Composable card and the PDF export can share the same parser.
private data class MiniBookMcq(
    val number: String,
    val question: String,
    val options: List<String>,
    val correctLetter: String?,
    val explanation: String?
)
private fun parseMiniBookMcqBlock(block: String): MiniBookMcq? {
    val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    val m = Regex("^Q?(\\d+)[.)]\\s*(.*)", RegexOption.IGNORE_CASE).find(lines[0]) ?: return null
    val number = m.groupValues[1]
    val question = m.groupValues[2]

    var correctLetter: String? = null
    var explanation: String? = null
    val options = mutableListOf<String>()
    lines.drop(1).forEach { line ->
        val ansMatch = Regex("^Correct Answer:\\s*\\(?([A-Da-d])[.)]?", RegexOption.IGNORE_CASE).find(line)
        val expMatch = Regex("^Explanation:\\s*(.*)", RegexOption.IGNORE_CASE).find(line)
        when {
            ansMatch != null -> correctLetter = ansMatch.groupValues[1].uppercase()
            expMatch != null -> explanation = expMatch.groupValues[1]
            else -> options.add(line.replace(Regex("^\\(?[A-Da-d][.)]\\s*"), ""))
        }
    }
    if (options.isEmpty()) return null
    return MiniBookMcq(number, question, options, correctLetter, explanation)
}

private fun pdfWrap(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
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

// Justified line drawing — stretches inter-word spaces to fill maxWidth. Last line of a
// paragraph is left-aligned (standard typographic rule), matching the in-app Compose behavior.
private fun pdfDrawJustified(
    canvas: android.graphics.Canvas,
    line: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    paint: android.graphics.Paint,
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

// Renders pasted-text book content to PDF: headings, plain paragraphs (justified), and MCQ
// cards (question + colored options + navy explanation card) — matching the in-app card design.
private fun saveMiniBookTextPdf(context: android.content.Context, title: String, body: String): String? {
    return try {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 30f
        val contentWidth = pageWidth - margin * 2
        val document = PdfDocument()

        val titlePaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 16f; isFakeBoldText = true }
        val bandPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x12, 0x20, 0x3D) }
        val bodyPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x1A, 0x1A, 0x1A); textSize = 12f }
        val headingPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x7A, 0x2E, 0x3D); textSize = 15f; isFakeBoldText = true }
        val qPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x1A, 0x1A, 0x1A); textSize = 13f; isFakeBoldText = true }
        val optCorrectPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x1F, 0x7A, 0x3D); textSize = 12f; isFakeBoldText = true }
        val optNormalPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x5B, 0x5F, 0x6B); textSize = 12f }
        val optCorrectBg = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xDC, 0xF5, 0xE0) }
        val optNormalBg = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xF2, 0xF2, 0xF2) }
        val whiteBg = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
        val cardBorderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0xD4, 0xA0, 0x17)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val explBgPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x12, 0x20, 0x3D) }
        val explHeaderPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xF0, 0xE6, 0xC8); textSize = 12.5f; isFakeBoldText = true }
        val explBodyPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xF0, 0xE6, 0xC8); textSize = 11.5f }
        val dotPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x4C, 0xAF, 0x50) }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 50f, bandPaint)
        canvas.drawText(title, margin, 32f, titlePaint)
        var y = 74f
        val marginBottom = 800f

        fun newPageIfNeeded(needed: Float) {
            if (y + needed > marginBottom) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 30f
            }
        }

        val blocks = body.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }

        blocks.forEach { block ->
            val mcq = parseMiniBookMcqBlock(block)
            when {
                mcq != null -> {
                    val qLines = pdfWrap("Q${mcq.number}. ${mcq.question}", qPaint, contentWidth - 24f)
                    val optWraps = mcq.options.mapIndexed { idx, opt ->
                        val letter = ('A' + idx).toString()
                        val isCorrect = mcq.correctLetter != null && letter == mcq.correctLetter
                        val prefix = if (isCorrect) "\u2713 " else ""
                        pdfWrap(prefix + opt, if (isCorrect) optCorrectPaint else optNormalPaint, contentWidth - 64f)
                    }
                    val explLines = if (!mcq.explanation.isNullOrBlank())
                        pdfWrap(mcq.explanation, explBodyPaint, contentWidth - 64f) else emptyList()

                    var cardHeight = 20f + qLines.size * 18f + 10f
                    optWraps.forEach { cardHeight += (it.size * 15f + 10f) + 4f }
                    if (explLines.isNotEmpty()) cardHeight += 8f + 16f + explLines.size * 14f + 14f
                    cardHeight += 16f

                    newPageIfNeeded(cardHeight)
                    val cardTop = y
                    canvas.drawRect(margin, cardTop, pageWidth - margin, cardTop + cardHeight, whiteBg)
                    canvas.drawRect(margin, cardTop, pageWidth - margin, cardTop + cardHeight, cardBorderPaint)

                    var ly = cardTop + 22f
                    qLines.forEachIndexed { idx, line ->
                        pdfDrawJustified(canvas, line, margin + 12f, ly, contentWidth - 24f, qPaint, idx == qLines.size - 1)
                        ly += 18f
                    }
                    ly += 8f

                    optWraps.forEachIndexed { oi, optLines ->
                        val letter = ('A' + oi).toString()
                        val isCorrect = mcq.correctLetter != null && letter == mcq.correctLetter
                        val boxHeight = optLines.size * 15f + 10f
                        val bg = if (isCorrect) optCorrectBg else optNormalBg
                        canvas.drawRect(margin + 12f, ly - 11f, pageWidth - margin - 12f, ly - 11f + boxHeight, bg)
                        val p = if (isCorrect) optCorrectPaint else optNormalPaint
                        var oy = ly
                        optLines.forEachIndexed { li, line ->
                            pdfDrawJustified(canvas, line, margin + 20f, oy, contentWidth - 64f, p, li == optLines.size - 1)
                            oy += 15f
                        }
                        ly += boxHeight + 4f
                    }

                    if (explLines.isNotEmpty()) {
                        ly += 4f
                        val boxTop = ly
                        val boxHeight = 16f + explLines.size * 14f + 12f
                        canvas.drawRect(margin + 12f, boxTop, pageWidth - margin - 12f, boxTop + boxHeight, explBgPaint)
                        canvas.drawCircle(margin + 22f, boxTop + 15f, 3f, dotPaint)
                        canvas.drawText("Explanation:", margin + 32f, boxTop + 18f, explHeaderPaint)
                        var ey = boxTop + 34f
                        explLines.forEachIndexed { idx, line ->
                            pdfDrawJustified(canvas, line, margin + 20f, ey, contentWidth - 64f, explBodyPaint, idx == explLines.size - 1)
                            ey += 14f
                        }
                    }
                    y = cardTop + cardHeight + 10f
                }
                block.startsWith("#") -> {
                    val text = block.removePrefix("#").trim()
                    val hLines = pdfWrap(text, headingPaint, contentWidth)
                    newPageIfNeeded(hLines.size * 18f + 10f)
                    hLines.forEach { line -> canvas.drawText(line, margin, y + 14f, headingPaint); y += 18f }
                    y += 8f
                }
                else -> {
                    val pLines = pdfWrap(block, bodyPaint, contentWidth)
                    newPageIfNeeded(pLines.size * 16f + 8f)
                    pLines.forEachIndexed { idx, line ->
                        pdfDrawJustified(canvas, line, margin, y + 12f, contentWidth, bodyPaint, idx == pLines.size - 1)
                        y += 16f
                    }
                    y += 8f
                }
            }
        }

        document.finishPage(page)

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
            } else { document.close(); null }
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

// Decodes a "data:application/pdf;base64,...." string straight from Realtime Database and
// writes the bytes to the Downloads folder — no network fetch needed since the file already
// came down with the rest of the book's data.
private fun savePdfBase64ToDownloads(context: android.content.Context, pdfBase64: String, title: String): String? {
    return try {
        val raw = if (pdfBase64.contains(",")) pdfBase64.substringAfter(",") else pdfBase64
        val bytes = Base64.decode(raw, Base64.DEFAULT)
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
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                "Downloads/$fileName"
            } else null
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }
}

private suspend fun com.google.android.gms.tasks.Task<com.google.firebase.database.DataSnapshot>.awaitValue(): Any? =
    suspendCancellableCoroutine { cont ->
        this.addOnSuccessListener { snapshot -> if (cont.isActive) cont.resume(snapshot.value, null) }
        this.addOnFailureListener { if (cont.isActive) cont.resume(null, null) }
    }

@Composable
fun MiniBookReaderScreen(bookKey: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("engteck_prefs", android.content.Context.MODE_PRIVATE) }

    var loading by remember { mutableStateOf(true) }
    var content by remember { mutableStateOf<String?>(null) }
    var pdfBase64 by remember { mutableStateOf<String?>(null) }
    var price by remember { mutableStateOf(0L) }
    var coverImageBase64 by remember { mutableStateOf<String?>(null) }
    var hasPdf by remember { mutableStateOf(false) }

    var unlocked by remember { mutableStateOf(prefs.getBoolean("sp_minibookpaid_$bookKey", false)) }
    var mobile by remember { mutableStateOf(prefs.getString("sp_mobile", "") ?: "") }
    var upiId by remember { mutableStateOf("") }
    var payingInProgress by remember { mutableStateOf(false) }
    var payMsg by remember { mutableStateOf("") }
    var checkingManual by remember { mutableStateOf(false) }
    var manualMsg by remember { mutableStateOf("") }
    var bookKeyForPayment by remember { mutableStateOf("") }

    fun incrementDownloadsAndMarkUnlocked() {
        prefs.edit().putBoolean("sp_minibookpaid_$bookKey", true).apply()
        unlocked = true
        val ref = FirebaseDatabase.getInstance().getReference("miniBooks").child(bookKey).child("downloads")
        ref.get().addOnSuccessListener { snap ->
            val current = snap.getValue(Long::class.java) ?: 0L
            ref.setValue(current + 1)
        }
    }

    fun bumpFreeDownload() {
        val ref = FirebaseDatabase.getInstance().getReference("miniBooks").child(bookKey).child("downloads")
        ref.get().addOnSuccessListener { snap ->
            val current = snap.getValue(Long::class.java) ?: 0L
            ref.setValue(current + 1)
        }
    }

    val checkoutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val paymentId = result.data?.getStringExtra("paymentId")
            if (!paymentId.isNullOrEmpty() && bookKeyForPayment.isNotEmpty()) {
                payMsg = "Payment successful! Confirming…"
                scope.launch {
                    var attemptsLeft = 20
                    var confirmed = false
                    while (attemptsLeft > 0 && !confirmed) {
                        val snap = FirebaseDatabase.getInstance()
                            .getReference("paidMiniBooks").child(mobile).child(bookKeyForPayment)
                            .get().awaitValue()
                        if (snap == true) {
                            confirmed = true
                            payingInProgress = false
                            payMsg = "Payment confirmed ✓"
                            incrementDownloadsAndMarkUnlocked()
                        } else {
                            attemptsLeft--
                            delay(2000)
                        }
                    }
                    if (!confirmed) {
                        payingInProgress = false
                        payMsg = "Payment confirmation is taking longer than usual — please try again in a moment."
                    }
                }
            } else {
                payingInProgress = false
                payMsg = "Payment went through but could not be confirmed — please try again."
            }
        } else {
            payingInProgress = false
            val err = result.data?.getStringExtra("error")
            payMsg = err ?: "Payment window closed. Tap \"Pay Now\" to try again."
        }
    }

    LaunchedEffect(bookKey) {
        val db = FirebaseDatabase.getInstance()
        db.getReference("miniBooks").child(bookKey).get()
            .addOnSuccessListener { s ->
                price = s.child("price").getValue(Long::class.java) ?: 0L
                coverImageBase64 = s.child("coverImageBase64").getValue(String::class.java)
                hasPdf = s.child("hasPdf").getValue(Boolean::class.java) ?: false
            }
        db.getReference("content").child("upiId").get()
            .addOnSuccessListener { upiId = it.getValue(String::class.java) ?: "" }
        db.getReference("miniBooksContent").child(bookKey).get()
            .addOnSuccessListener { snapshot ->
                loading = false
                content = snapshot.child("pastedText").getValue(String::class.java)
                pdfBase64 = snapshot.child("pdfBase64").getValue(String::class.java)
            }
            .addOnFailureListener { loading = false }
    }

    fun startPayNow() {
        if (mobile.length != 10) { payMsg = "Please enter a valid 10-digit mobile number."; return }
        if (context !is Activity) { payMsg = "Could not start payment."; return }
        payingInProgress = true
        payMsg = ""
        val key = "book_${bookKey}_${System.currentTimeMillis()}"
        bookKeyForPayment = key
        scope.launch {
            val order = createRazorpayOrder(price.toInt(), key, mobile, title)
            if (order == null) {
                payingInProgress = false
                payMsg = "Instant payment isn't set up yet. Try again shortly, or pay via the UPI QR below."
                return@launch
            }
            prefs.edit().putString("sp_mobile", mobile).apply()
            val intent = buildRazorpayCheckoutIntent(context, order, mobile, title, "Shree English Classes")
            checkoutLauncher.launch(intent)
        }
    }

    fun startCheckAccess() {
        if (mobile.length != 10) { manualMsg = "Please enter a valid 10-digit mobile number."; return }
        checkingManual = true
        manualMsg = "Checking…"
        scope.launch {
            var attemptsLeft = 5
            var confirmed = false
            while (attemptsLeft > 0 && !confirmed) {
                val snap = FirebaseDatabase.getInstance()
                    .getReference("paidMiniBooks").child(mobile).child(bookKey)
                    .get().awaitValue()
                if (snap == true) {
                    confirmed = true
                    checkingManual = false
                    manualMsg = "Payment confirmed ✓"
                    prefs.edit().putString("sp_mobile", mobile).apply()
                    incrementDownloadsAndMarkUnlocked()
                } else {
                    attemptsLeft--
                    if (attemptsLeft > 0) delay(1500)
                }
            }
            if (!confirmed) {
                checkingManual = false
                manualMsg = "Payment not confirmed yet. If you've already paid, wait a moment and try again — or ask the admin to Mark as Paid."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) { Text("‹ Back") }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (coverImageBase64 != null) {
                AsyncImage(
                    model = coverImageBase64,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFFF5F3EC), RoundedCornerShape(14.dp)).padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(14.dp))
            }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D), modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(16.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF12203D))
                }
            } else if (hasPdf) {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    MiniBookPdfPreview(
                        pdfBase64 = pdfBase64,
                        unlocked = price == 0L || unlocked,
                        freePageCount = MINIBOOK_FREE_SECTIONS
                    )
                }
                if (price == 0L || unlocked) {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            if (unlocked) "Payment confirmed ✓ — you can now download the full PDF." else "This book is free.",
                            fontSize = 13.sp, color = Color(0xFF1F7A3D), fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val pb64 = pdfBase64
                                if (pb64 == null) {
                                    Toast.makeText(context, "PDF data not found", Toast.LENGTH_SHORT).show()
                                } else {
                                    val path = savePdfBase64ToDownloads(context, pb64, title)
                                    if (path != null) {
                                        if (price == 0L) bumpFreeDownload()
                                        Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Download failed, try again", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B79)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("↓ Download Full PDF", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        MiniBookPaywall(
                            price = price, mobile = mobile, upiId = upiId,
                            payingInProgress = payingInProgress, payMsg = payMsg,
                            checkingManual = checkingManual, manualMsg = manualMsg,
                            coachingName = "Shree English Classes",
                            onMobileChange = { mobile = it },
                            onPayNow = { startPayNow() },
                            onCheckAccess = { startCheckAccess() }
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            } else if (content == null) {
                Text("No content found.", fontSize = 13.sp, color = Color(0xFF5B5F6B), modifier = Modifier.padding(horizontal = 20.dp))
            } else {
                val blocks = content!!.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
                val freeBlocks = if (price == 0L || unlocked) blocks else blocks.take(MINIBOOK_FREE_SECTIONS)

                freeBlocks.forEach { block ->
                    val mcq = parseMiniBookMcqBlock(block)
                    when {
                        mcq != null -> {
                            MiniBookMcqCard(mcq)
                            Spacer(Modifier.height(14.dp))
                        }
                        block.startsWith("#") -> {
                            Text(
                                block.removePrefix("#").trim(),
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A2E3D),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                        }
                        else -> {
                            JustifiedText(
                                block, fontSize = 14.sp, color = Color(0xFF1A1A1A),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp)
                            )
                        }
                    }
                }

                if (price > 0 && !unlocked && blocks.size > MINIBOOK_FREE_SECTIONS) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        MiniBookPaywall(
                            price = price, mobile = mobile, upiId = upiId,
                            payingInProgress = payingInProgress, payMsg = payMsg,
                            checkingManual = checkingManual, manualMsg = manualMsg,
                            coachingName = "Shree English Classes",
                            onMobileChange = { mobile = it },
                            onPayNow = { startPayNow() },
                            onCheckAccess = { startCheckAccess() }
                        )
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Button(
                            onClick = {
                                val path = saveMiniBookTextPdf(context, title, content ?: "")
                                if (path != null) {
                                    if (price == 0L) bumpFreeDownload()
                                    Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Download failed, try again", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B79)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("↓ Download Full PDF", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

// Manual justify — native TextAlign.Justify doesn't render reliably on all devices/OS
// versions, so this measures each word itself and stretches the inter-word gaps to fill
// the full line width (except the last line of the paragraph, which stays left-aligned —
// standard typographic rule). Behaves like a browser's text-align: justify.
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
private fun MiniBookMcqCard(mcq: MiniBookMcq) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(0.dp))
            .padding(vertical = 18.dp, horizontal = 20.dp)
    ) {
        JustifiedText(
            "Q${mcq.number}. ${mcq.question}",
            fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        mcq.options.forEachIndexed { idx, opt ->
            val letter = ('A' + idx).toString()
            val isCorrect = mcq.correctLetter != null && letter == mcq.correctLetter
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(if (isCorrect) Color(0xFFDCF5E0) else Color(0xFFF2F2F2), RoundedCornerShape(10.dp))
                    .then(
                        if (isCorrect) Modifier.border(1.5.dp, Color(0xFF1F7A3D), RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .padding(vertical = 16.dp, horizontal = 18.dp)
            ) {
                JustifiedText(
                    if (isCorrect) "✓ $opt" else opt,
                    fontSize = 15.sp,
                    color = if (isCorrect) Color(0xFF1F7A3D) else Color(0xFF5B5F6B),
                    fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (!mcq.explanation.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12203D), RoundedCornerShape(10.dp))
                    .padding(vertical = 14.dp, horizontal = 16.dp),
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
                    Text("Explanation:", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF5E6B8))
                    Spacer(Modifier.height(4.dp))
                    JustifiedText(
                        mcq.explanation,
                        fontSize = 13.5.sp, color = Color(0xFFF5E6B8),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniBookPdfPreview(pdfBase64: String?, unlocked: Boolean, freePageCount: Int) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var pageBitmaps by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var totalPages by remember { mutableStateOf(0) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(pdfBase64, unlocked) {
        if (pdfBase64 == null) { loading = false; errorMsg = "PDF data not found."; return@LaunchedEffect }
        loading = true
        errorMsg = ""
        withContext(Dispatchers.IO) {
            try {
                val raw = if (pdfBase64.contains(",")) pdfBase64.substringAfter(",") else pdfBase64
                val bytes = Base64.decode(raw, Base64.DEFAULT)
                val tempFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
                FileOutputStream(tempFile).use { it.write(bytes) }

                val pfd = android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                val howMany = if (unlocked) pageCount else minOf(freePageCount, pageCount)
                val bitmaps = mutableListOf<android.graphics.Bitmap>()
                for (i in 0 until howMany) {
                    val page = renderer.openPage(i)
                    val scale = 2
                    val bmp = android.graphics.Bitmap.createBitmap(page.width * scale, page.height * scale, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bmp)
                    page.close()
                }
                renderer.close()
                pfd.close()
                tempFile.delete()

                withContext(Dispatchers.Main) {
                    pageBitmaps = bitmaps
                    totalPages = pageCount
                    loading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loading = false
                    errorMsg = "Could not open this PDF for preview."
                }
            }
        }
    }

    when {
        loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        }
        errorMsg.isNotEmpty() -> Text(errorMsg, fontSize = 13.sp, color = Color(0xFFC0392B))
        else -> {
            Column {
                pageBitmaps.forEach { bmp ->
                    ZoomablePdfPage(bmp)
                    Spacer(Modifier.height(10.dp))
                }
                if (!unlocked && totalPages > freePageCount) {
                    Text(
                        "Showing $freePageCount of $totalPages pages",
                        fontSize = 11.5.sp, color = Color(0xFF5B5F6B), modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

// A single PDF page image with pinch-to-zoom + pan, reset on double-tap. Only intercepts touch
// when there are 2+ fingers (an actual pinch) or the page is already zoomed in — a normal
// single-finger drag at 1x scale is left alone so the page scrolls normally.
@Composable
private fun ZoomablePdfPage(bmp: android.graphics.Bitmap) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
    ) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val isPinch = event.changes.size > 1
                            if (isPinch || scale > 1f) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                scale = newScale
                                if (newScale > 1f) {
                                    offsetX += panChange.x
                                    offsetY += panChange.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                event.changes.forEach { c -> if (c.positionChanged()) c.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    })
                }
        )
    }
}

@Composable
private fun MiniBookPaywall(
    price: Long,
    mobile: String,
    upiId: String,
    payingInProgress: Boolean,
    payMsg: String,
    checkingManual: Boolean,
    manualMsg: String,
    coachingName: String,
    onMobileChange: (String) -> Unit,
    onPayNow: () -> Unit,
    onCheckAccess: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAF8F3), RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("🔒 To read the full book / download the PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        Text("₹$price", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = mobile,
            onValueChange = { onMobileChange(it.filter { c -> c.isDigit() }.take(10)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            placeholder = { Text("10-digit mobile number") }
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPayNow,
            enabled = !payingInProgress,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F7A3D)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(if (payingInProgress) "Please wait..." else "⚡ Pay Now — Instant Unlock", color = Color.White, fontWeight = FontWeight.Bold)
        }
        if (payMsg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(payMsg, fontSize = 11.5.sp, color = if (payMsg.contains("✓")) Color(0xFF1F7A3D) else Color(0xFFC0392B))
        }

        if (upiId.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("— OR —", fontSize = 11.sp, color = Color(0xFF9B968A))
            Spacer(Modifier.height(10.dp))
            Text("Scan & Pay via any UPI app", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
            Spacer(Modifier.height(10.dp))
            val upiLink = "upi://pay?pa=" + java.net.URLEncoder.encode(upiId, "UTF-8") +
                "&pn=" + java.net.URLEncoder.encode(coachingName, "UTF-8") +
                "&am=" + price + "&cu=INR"
            val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=" + java.net.URLEncoder.encode(upiLink, "UTF-8")
            AsyncImage(
                model = qrUrl,
                contentDescription = "UPI QR Code",
                modifier = Modifier.size(180.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("UPI ID: $upiId", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B6B79))
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onCheckAccess,
                enabled = !checkingManual,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B79)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(if (checkingManual) "Checking..." else "I've Paid — Check Access", color = Color.White, fontWeight = FontWeight.Bold)
            }
            if (manualMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(manualMsg, fontSize = 11.5.sp, color = if (manualMsg.contains("✓")) Color(0xFF1F7A3D) else Color(0xFFC0392B))
            }
        }
    }
}

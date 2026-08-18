package com.shreeyog.engteck.screens

import android.app.Activity
import android.content.ContentValues
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import java.net.HttpURLConnection
import java.net.URL

private const val MINIBOOK_FREE_SECTIONS = 5

private fun saveMiniBookTextPdf(context: android.content.Context, title: String, body: String): String? {
    return try {
        val pageWidth = 595
        val pageHeight = 842
        val document = PdfDocument()
        val titlePaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 16f; isFakeBoldText = true }
        val bandPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x12, 0x20, 0x3D) }
        val bodyPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x1A, 0x1A, 0x1A); textSize = 12f }
        val margin = 30f
        val contentWidth = pageWidth - margin * 2

        fun wrap(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
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
        body.split("\n").forEach { p -> allLines.addAll(wrap(p, bodyPaint, contentWidth)) }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 50f, bandPaint)
        canvas.drawText(title, margin, 32f, titlePaint)
        var y = 74f
        for (line in allLines) {
            if (y > 800f) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 40f
            }
            canvas.drawText(line, margin, y, bodyPaint)
            y += 16f
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

private suspend fun downloadPdfFromUrl(context: android.content.Context, url: String, title: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()

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
    var price by remember { mutableStateOf(0L) }
    var coverImageUrl by remember { mutableStateOf<String?>(null) }
    var pdfUrl by remember { mutableStateOf<String?>(null) }

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
                        payMsg = "Payment confirm hone mein zyada time lag raha hai — thodi der baad dobara try karo."
                    }
                }
            } else {
                payingInProgress = false
                payMsg = "Payment ho gaya lekin confirm nahi ho paya — dobara try karo."
            }
        } else {
            payingInProgress = false
            val err = result.data?.getStringExtra("error")
            payMsg = err ?: "Payment window band ho gayi. Dobara \"Pay Now\" dabao."
        }
    }

    LaunchedEffect(bookKey) {
        val db = FirebaseDatabase.getInstance()
        db.getReference("miniBooks").child(bookKey).get()
            .addOnSuccessListener { s ->
                price = s.child("price").getValue(Long::class.java) ?: 0L
                coverImageUrl = s.child("coverImageUrl").getValue(String::class.java)
                pdfUrl = s.child("pdfUrl").getValue(String::class.java)
            }
        db.getReference("content").child("upiId").get()
            .addOnSuccessListener { upiId = it.getValue(String::class.java) ?: "" }
        db.getReference("miniBooksContent").child(bookKey).child("pastedText").get()
            .addOnSuccessListener { snapshot ->
                loading = false
                content = snapshot.getValue(String::class.java)
            }
            .addOnFailureListener { loading = false }
    }

    fun startPayNow() {
        if (mobile.length != 10) { payMsg = "Pehle 10-digit mobile number bharo."; return }
        if (context !is Activity) { payMsg = "Payment shuru nahi ho paya."; return }
        payingInProgress = true
        payMsg = ""
        val key = "book_${bookKey}_${System.currentTimeMillis()}"
        bookKeyForPayment = key
        scope.launch {
            val order = createRazorpayOrder(price.toInt(), key, mobile, title)
            if (order == null) {
                payingInProgress = false
                payMsg = "Instant payment abhi setup nahi hua. Thodi der baad try karo ya UPI QR se pay karo."
                return@launch
            }
            prefs.edit().putString("sp_mobile", mobile).apply()
            val intent = buildRazorpayCheckoutIntent(context, order, mobile, title, "Shree English Classes")
            checkoutLauncher.launch(intent)
        }
    }

    fun startCheckAccess() {
        if (mobile.length != 10) { manualMsg = "Pehle 10-digit mobile number bharo."; return }
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
                manualMsg = "Abhi tak payment confirm nahi hua. Pay karne ke baad thodi der wait karke dobara try karo, ya admin se \"Mark as Paid\" karwao."
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (coverImageUrl != null) {
                AsyncImage(
                    model = coverImageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFFF5F3EC), RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.height(14.dp))
            }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(16.dp))

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF12203D))
                }
            } else if (pdfUrl != null) {
                if (price == 0L || unlocked) {
                    Text(
                        if (unlocked) "Payment confirmed ✓ — poori PDF download ho sakti hai." else "Ye book free hai.",
                        fontSize = 13.sp, color = Color(0xFF1F7A3D), fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val path = downloadPdfFromUrl(context, pdfUrl!!, title)
                                if (path != null) {
                                    if (price == 0L) {
                                        val ref = FirebaseDatabase.getInstance().getReference("miniBooks").child(bookKey).child("downloads")
                                        ref.get().addOnSuccessListener { snap ->
                                            val current = snap.getValue(Long::class.java) ?: 0L
                                            ref.setValue(current + 1)
                                        }
                                    }
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
                } else {
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
            } else if (content == null) {
                Text("No content found.", fontSize = 13.sp, color = Color(0xFF5B5F6B))
            } else {
                val blocks = content!!.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
                val freeBlocks = if (price == 0L || unlocked) blocks else blocks.take(MINIBOOK_FREE_SECTIONS)

                freeBlocks.forEach { block ->
                    if (block.startsWith("#")) {
                        Text(
                            block.removePrefix("#").trim(),
                            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A2E3D),
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                        )
                    } else {
                        Text(block, fontSize = 14.sp, color = Color(0xFF1A1A1A), lineHeight = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }

                if (price > 0 && !unlocked && blocks.size > MINIBOOK_FREE_SECTIONS) {
                    Spacer(Modifier.height(10.dp))
                    MiniBookPaywall(
                        price = price, mobile = mobile, upiId = upiId,
                        payingInProgress = payingInProgress, payMsg = payMsg,
                        checkingManual = checkingManual, manualMsg = manualMsg,
                        coachingName = "Shree English Classes",
                        onMobileChange = { mobile = it },
                        onPayNow = { startPayNow() },
                        onCheckAccess = { startCheckAccess() }
                    )
                } else {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val path = saveMiniBookTextPdf(context, title, content ?: "")
                            if (path != null) {
                                if (price == 0L) {
                                    val ref = FirebaseDatabase.getInstance().getReference("miniBooks").child(bookKey).child("downloads")
                                    ref.get().addOnSuccessListener { snap ->
                                        val current = snap.getValue(Long::class.java) ?: 0L
                                        ref.setValue(current + 1)
                                    }
                                }
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
                Spacer(Modifier.height(30.dp))
            }
        }
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
        Text("🔒 Poori book padhne / download karne ke liye", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
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

package com.shreeyog.engteck.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class AdminMiniBookEntry(val key: String, val title: String, val price: Long, val downloads: Long)

private fun readBytes(context: android.content.Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }
}

private fun compressImageToBase64(context: android.content.Context, uri: Uri, maxWidth: Int = 500, quality: Int = 70): String? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(input)
        input.close()
        if (original == null) return null
        val scale = if (original.width > maxWidth) maxWidth.toFloat() / original.width else 1f
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
        } else original
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun AdminMiniBookUploadCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("text") }
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfFileSizeKb by remember { mutableStateOf(0) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pdfUri = uri
            val bytes = readBytes(context, uri)
            pdfFileSizeKb = (bytes?.size ?: 0) / 1024
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) coverUri = uri }

    var books by remember { mutableStateOf<List<AdminMiniBookEntry>>(emptyList()) }
    var loadingBooks by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableStateOf(0) }
    var editBook by remember { mutableStateOf<AdminMiniBookEntry?>(null) }

    LaunchedEffect(refreshTick) {
        loadingBooks = true
        FirebaseDatabase.getInstance().getReference("miniBooks")
            .get()
            .addOnSuccessListener { snapshot ->
                loadingBooks = false
                books = snapshot.children.mapNotNull { c ->
                    val key = c.key ?: return@mapNotNull null
                    AdminMiniBookEntry(
                        key,
                        c.child("title").getValue(String::class.java) ?: "Untitled",
                        c.child("price").getValue(Long::class.java) ?: 0L,
                        c.child("downloads").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.key }
            }
            .addOnFailureListener { loadingBooks = false }
    }

    fun resetForm() {
        title = ""; price = ""; content = ""; pdfUri = null; pdfFileSizeKb = 0; coverUri = null
    }

    fun uploadBook() {
        if (title.isBlank()) { status = "Please enter a title."; return }
        if (mode == "text" && content.isBlank()) { status = "Please paste the content."; return }
        if (mode == "pdf" && pdfUri == null) { status = "Please select a PDF file first."; return }
        if (mode == "pdf" && pdfFileSizeKb > 8000) {
            status = "This PDF is too large (${pdfFileSizeKb / 1024}MB). Please keep it under 8MB."
            return
        }

        saving = true
        status = ""
        val db = FirebaseDatabase.getInstance()
        val newRef = db.getReference("miniBooks").push()
        val key = newRef.key
        if (key == null) { saving = false; status = "Could not generate a key."; return }

        scope.launch {
            val coverBase64 = if (coverUri != null) {
                withContext(Dispatchers.IO) { compressImageToBase64(context, coverUri!!) }
            } else null

            val pdfBase64 = if (mode == "pdf" && pdfUri != null) {
                withContext(Dispatchers.IO) {
                    val bytes = readBytes(context, pdfUri!!)
                    if (bytes != null) "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP) else null
                }
            } else null

            if (mode == "pdf" && pdfBase64 == null) {
                saving = false
                status = "Could not read the PDF file."
                return@launch
            }

            val bookData = mutableMapOf<String, Any>(
                "title" to title,
                "addedAt" to System.currentTimeMillis(),
                "downloads" to 0,
                "price" to (price.toLongOrNull() ?: 0L)
            )
            if (coverBase64 != null) bookData["coverImageBase64"] = coverBase64
            if (pdfBase64 != null) bookData["hasPdf"] = true

            newRef.setValue(bookData)
                .addOnSuccessListener {
                    val contentData = mutableMapOf<String, Any>()
                    if (mode == "text") contentData["pastedText"] = content
                    if (pdfBase64 != null) contentData["pdfBase64"] = pdfBase64

                    db.getReference("miniBooksContent").child(key)
                        .setValue(contentData)
                        .addOnSuccessListener { saving = false; status = "Book uploaded ✓"; resetForm(); refreshTick++ }
                        .addOnFailureListener { saving = false; status = "Failed to save content" }
                }
                .addOnFailureListener { saving = false; status = "Failed to upload" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Special Note Books", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(4.dp))
        Text(
            "Text-based books show the first 5 sections free. PDF books show only the cover + title until unlocked. Set price to ₹0 for a fully free book.",
            fontSize = 11.sp,
            color = Color(0xFF5B5F6B)
        )
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("text" to "📝 Paste Text", "pdf" to "📄 Upload PDF").forEach { (key, label) ->
                val active = mode == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC), RoundedCornerShape(100.dp))
                        .clickable { mode = key }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Text("Book Title", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        Text("Price (₹) — 0 = fully free", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = price,
            onValueChange = { price = it.filter { c -> c.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            placeholder = { Text("e.g. 49") }
        )
        Spacer(Modifier.height(12.dp))

        Text("Cover Image (optional)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(4.dp))
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = "Cover preview",
                modifier = Modifier.height(140.dp).fillMaxWidth().background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.height(6.dp))
        }
        OutlinedButton(
            onClick = { coverPicker.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(if (coverUri == null) "🖼️ Choose Cover Image" else "🖼️ Change Cover Image")
        }
        Spacer(Modifier.height(12.dp))

        if (mode == "text") {
            Text("Content", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("# Chapter Heading\n\nParagraph text here...") }
            )
        } else {
            Text("PDF File (max ~8MB)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { pdfPicker.launch("application/pdf") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (pdfUri == null) "📄 Choose PDF File" else "📄 PDF Selected (${pdfFileSizeKb}KB) — Change")
            }
        }
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = { uploadBook() },
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(if (saving) "Uploading..." else "Upload Book", fontWeight = FontWeight.Bold)
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(status, fontSize = 12.sp, color = if (status.contains("✓")) Color(0xFF1F7A3D) else Color(0xFFC0392B))
        }

        Spacer(Modifier.height(18.dp))
        Text("Uploaded Books", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(8.dp))

        if (loadingBooks) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        } else if (books.isEmpty()) {
            Text("No books uploaded yet.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
        } else {
            Column(modifier = Modifier.heightIn(max = 360.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(books) { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(b.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                                Text(
                                    "${if (b.price > 0) "₹${b.price}" else "Free"} · ${b.downloads} downloads",
                                    fontSize = 10.5.sp, color = Color(0xFF5B5F6B)
                                )
                            }
                            TextButton(onClick = { editBook = b }) { Text("Edit") }
                        }
                    }
                }
            }
        }
    }

    editBook?.let { book ->
        EditMiniBookDialog(
            book = book,
            onDismiss = { editBook = null },
            onSaved = { editBook = null; refreshTick++ }
        )
    }
}

@Composable
private fun EditMiniBookDialog(book: AdminMiniBookEntry, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var price by remember { mutableStateOf(book.price.toString()) }
    var downloads by remember { mutableStateOf(book.downloads.toString()) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(book.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(14.dp))

            Text("Price (₹)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = price,
                onValueChange = { price = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Text("Total Downloads (you can set this manually)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = downloads,
                onValueChange = { downloads = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(errorMsg, fontSize = 11.5.sp, color = Color(0xFFC0392B))
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        saving = true
                        val updates = mapOf(
                            "price" to (price.toLongOrNull() ?: 0L),
                            "downloads" to (downloads.toLongOrNull() ?: 0L)
                        )
                        FirebaseDatabase.getInstance().getReference("miniBooks").child(book.key)
                            .updateChildren(updates)
                            .addOnSuccessListener { saving = false; onSaved() }
                            .addOnFailureListener { saving = false; errorMsg = "Failed to save." }
                    },
                    enabled = !saving && !deleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text(if (saving) "Saving..." else "Save", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        deleting = true
                        val db = FirebaseDatabase.getInstance()
                        db.getReference("miniBooks").child(book.key).removeValue()
                            .addOnSuccessListener {
                                db.getReference("miniBooksContent").child(book.key).removeValue()
                                    .addOnSuccessListener { deleting = false; onSaved() }
                                    .addOnFailureListener { deleting = false; onSaved() }
                            }
                            .addOnFailureListener { deleting = false; errorMsg = "Failed to delete." }
                    },
                    enabled = !saving && !deleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D4C), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(if (deleting) "..." else "Delete", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

package com.shreeyog.engteck.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import com.google.firebase.storage.FirebaseStorage

data class AdminMiniBookEntry(
    val key: String,
    val title: String,
    val price: Long,
    val downloads: Long,
    val coverImageUrl: String?,
    val pdfUrl: String?
)

@Composable
fun AdminMiniBookUploadCard() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("text") } // "text" | "pdf"
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pdfUri = uri }
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
                        c.child("downloads").getValue(Long::class.java) ?: 0L,
                        c.child("coverImageUrl").getValue(String::class.java),
                        c.child("pdfUrl").getValue(String::class.java)
                    )
                }.sortedByDescending { it.key }
            }
            .addOnFailureListener { loadingBooks = false }
    }

    fun resetForm() {
        title = ""; price = ""; content = ""; pdfUri = null; coverUri = null
    }

    fun uploadBook() {
        if (title.isBlank()) { status = "Title bharo."; return }
        if (mode == "text" && content.isBlank()) { status = "Content paste karo."; return }
        if (mode == "pdf" && pdfUri == null) { status = "Pehle PDF file select karo."; return }

        saving = true
        status = ""
        uploadProgress = ""
        val db = FirebaseDatabase.getInstance()
        val storage = FirebaseStorage.getInstance()
        val newRef = db.getReference("miniBooks").push()
        val key = newRef.key
        if (key == null) { saving = false; status = "Key generate nahi hua."; return }

        fun finalizeBookRecord(coverUrl: String?, pdfDownloadUrl: String?) {
            val bookData = mutableMapOf<String, Any>(
                "title" to title,
                "addedAt" to System.currentTimeMillis(),
                "downloads" to 0,
                "price" to (price.toLongOrNull() ?: 0L)
            )
            if (coverUrl != null) bookData["coverImageUrl"] = coverUrl
            if (pdfDownloadUrl != null) bookData["pdfUrl"] = pdfDownloadUrl

            newRef.setValue(bookData)
                .addOnSuccessListener {
                    if (mode == "text") {
                        db.getReference("miniBooksContent").child(key)
                            .setValue(mapOf("pastedText" to content))
                            .addOnSuccessListener { saving = false; status = "Book uploaded ✓"; resetForm(); refreshTick++ }
                            .addOnFailureListener { saving = false; status = "Failed to save content" }
                    } else {
                        saving = false
                        status = "Book uploaded ✓"
                        resetForm()
                        refreshTick++
                    }
                }
                .addOnFailureListener { saving = false; status = "Failed to upload" }
        }

        fun uploadPdfThenFinalize(coverUrl: String?) {
            if (mode == "pdf" && pdfUri != null) {
                uploadProgress = "Uploading PDF…"
                val pdfRef = storage.reference.child("miniBookPdfs/$key.pdf")
                pdfRef.putFile(pdfUri!!)
                    .addOnSuccessListener {
                        pdfRef.downloadUrl.addOnSuccessListener { url -> finalizeBookRecord(coverUrl, url.toString()) }
                            .addOnFailureListener { saving = false; status = "Failed to get PDF link" }
                    }
                    .addOnFailureListener { saving = false; status = "PDF upload failed" }
            } else {
                finalizeBookRecord(coverUrl, null)
            }
        }

        if (coverUri != null) {
            uploadProgress = "Uploading cover…"
            val coverRef = storage.reference.child("miniBookCovers/$key.jpg")
            coverRef.putFile(coverUri!!)
                .addOnSuccessListener {
                    coverRef.downloadUrl.addOnSuccessListener { url -> uploadPdfThenFinalize(url.toString()) }
                        .addOnFailureListener { saving = false; status = "Failed to get cover link" }
                }
                .addOnFailureListener { saving = false; status = "Cover upload failed" }
        } else {
            uploadPdfThenFinalize(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Upload Mini Book", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(4.dp))
        Text(
            "Text wale books mein pehle 5 sections free milte hain. PDF wale books mein poori PDF unlock hone se pehle sirf cover + title dikhta hai. Price ₹0 rakhoge to poori book free hogi.",
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
                placeholder = { Text("# Chapter Heading\n\nParagraph text yahan...") }
            )
        } else {
            Text("PDF File", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { pdfPicker.launch("application/pdf") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (pdfUri == null) "📄 Choose PDF File" else "📄 PDF Selected — Change")
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
            Text(if (saving) (uploadProgress.ifEmpty { "Uploading..." }) else "Upload Book", fontWeight = FontWeight.Bold)
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
            Text("Koi book upload nahi hui abhi.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
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
                                    "${if (b.price > 0) "₹${b.price}" else "Free"} · ${b.downloads} downloads · ${if (b.pdfUrl != null) "PDF" else "Text"}",
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

            Text("Total Downloads (manually set kar sakte ho)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
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

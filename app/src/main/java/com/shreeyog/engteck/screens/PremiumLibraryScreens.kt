package com.shreeyog.engteck.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase

// Hosted on the same real GitHub Pages domain as the Shree English Classes
// website — YouTube trusts this origin exactly like it trusts the website,
// which is why this actually plays in-app instead of getting rejected.
private const val VIDEO_EMBED_HOST = "https://dadu2122.github.io/Shree-English-Classes/video-embed.html"

private val PREMIUM_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)

data class LibraryItem(val title: String, val url: String, val date: String = "")

private const val PREFS_NAME = "engteck_prefs"
private const val KEY_STUDENT_MOBILE = "student_mobile"

private fun getSavedMobile(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_STUDENT_MOBILE, "") ?: ""

private fun saveMobile(context: Context, mobile: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_STUDENT_MOBILE, mobile).apply()
}

private fun extractYouTubeId(url: String): String? {
    val patterns = listOf(
        Regex("youtu\\.be/([a-zA-Z0-9_-]{6,})"),
        Regex("youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{6,})"),
        Regex("youtube\\.com/embed/([a-zA-Z0-9_-]{6,})"),
        Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{6,})")
    )
    for (p in patterns) {
        val m = p.find(url)
        if (m != null) return m.groupValues[1]
    }
    return null
}

private val VIDEO_PALETTES = listOf(
    listOf(Color(0xFF1B6B79), Color(0xFF12203D)),
    listOf(Color(0xFFD4A017), Color(0xFF7A2E3D)),
    listOf(Color(0xFF3B6EA8), Color(0xFF12203D)),
    listOf(Color(0xFF7A2E3D), Color(0xFF2B0F16)),
    listOf(Color(0xFF1F7A3D), Color(0xFF0B1730)),
    listOf(Color(0xFFE85D4C), Color(0xFF7A2E3D))
)
private fun videoGradient(str: String): Brush {
    var hash = 0
    for (c in str) { hash = (hash * 31 + c.code) }
    val idx = ((hash % VIDEO_PALETTES.size) + VIDEO_PALETTES.size) % VIDEO_PALETTES.size
    return Brush.linearGradient(VIDEO_PALETTES[idx])
}

@Composable
fun VideoLibraryCard() {
    PremiumLibraryShell(
        firebasePath = "videoLibrary",
        label = "PREMIUM",
        icon = "🎬",
        title = "Video Library",
        countChildPath = "videos",
        canOpenItems = true
    )
}

@Composable
fun ClassRecordingsCard() {
    Column {
        Text(
            "जो भी live class miss हो गई, या दोबारा सुननी हो — यहां से देखो।",
            fontSize = 12.sp,
            color = Color(0xFF5B5F6B),
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
    PremiumLibraryShell(
        firebasePath = "classRecordings",
        label = "PREMIUM",
        icon = "📼",
        title = "Class Recordings",
        countChildPath = "recordings",
        canOpenItems = true
    )
}

@Composable
fun PaidPdfLibraryCard() {
    PremiumLibraryShell(
        firebasePath = "paidPdfLibrary",
        label = "PREMIUM",
        icon = "📚",
        title = "PDF Library",
        countChildPath = "sets",
        canOpenItems = false
    )
}

@Composable
private fun PremiumLibraryShell(
    firebasePath: String,
    label: String,
    icon: String,
    title: String,
    countChildPath: String,
    canOpenItems: Boolean
) {
    val context = LocalContext.current
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var unlockMsg by remember { mutableStateOf("") }

    var mobile by remember { mutableStateOf(getSavedMobile(context)) }
    var showMobileDialog by remember { mutableStateOf(false) }
    var pendingCatKey by remember { mutableStateOf("") }
    var pendingCatLabel by remember { mutableStateOf("") }

    var checkingCatKey by remember { mutableStateOf("") }

    // Unlocked category's items are shown as an inline scrolling shelf — no popup.
    var unlockedCatLabel by remember { mutableStateOf("") }
    var unlockedItems by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var playingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(firebasePath) {
        FirebaseDatabase.getInstance().getReference(firebasePath)
            .get()
            .addOnSuccessListener { snapshot ->
                val result = mutableMapOf<String, Int>()
                for (catSnap in snapshot.children) {
                    val count = catSnap.child(countChildPath).childrenCount.toInt()
                    result[catSnap.key ?: ""] = count
                }
                counts = result
            }
    }

    fun openCategory(catKey: String, catLabel: String) {
        if (!canOpenItems) {
            unlockMsg = "$catLabel unlock karne ke liye payment system jald hi native app me aayega"
            return
        }
        val currentMobile = getSavedMobile(context)
        if (currentMobile.length != 10) {
            pendingCatKey = catKey
            pendingCatLabel = catLabel
            showMobileDialog = true
            return
        }
        checkingCatKey = catKey
        unlockMsg = ""
        playingIndex = null
        FirebaseDatabase.getInstance().getReference("paidVideoCategories").child(currentMobile).child(catKey)
            .get()
            .addOnSuccessListener { snap ->
                val isPaid = snap.getValue(Boolean::class.java) ?: false
                if (!isPaid) {
                    checkingCatKey = ""
                    unlockedCatLabel = ""
                    unlockMsg = "$catLabel abhi unlock nahi hai — payment ke baad access milega. Teacher se sampark karein."
                    return@addOnSuccessListener
                }
                FirebaseDatabase.getInstance().getReference(firebasePath).child(catKey).child(countChildPath)
                    .get()
                    .addOnSuccessListener { itemsSnap ->
                        checkingCatKey = ""
                        unlockedItems = itemsSnap.children.map { c ->
                            LibraryItem(
                                title = c.child("title").getValue(String::class.java) ?: "Untitled",
                                url = c.child("url").getValue(String::class.java) ?: "",
                                date = c.child("date").getValue(String::class.java) ?: ""
                            )
                        }
                        unlockedCatLabel = catLabel
                    }
                    .addOnFailureListener { checkingCatKey = ""; unlockMsg = "Load karne me dikkat aayi, dobara try karein." }
            }
            .addOnFailureListener { checkingCatKey = ""; unlockMsg = "Load karne me dikkat aayi, dobara try karein." }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Color(0xFF1F9D55)
        )
        Spacer(Modifier.height(6.dp))
        Text("$icon $title", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(14.dp))

        PREMIUM_CATS.forEach { (key, catLabel) ->
            val count = counts[key] ?: 0
            val isActiveShelf = unlockedCatLabel == catLabel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .background(Color(0xFFF5F3EC), RoundedCornerShape(14.dp))
                    .border(1.5.dp, if (isActiveShelf) Color(0xFF1F7A3D) else Color(0xFFD4A017), RoundedCornerShape(14.dp))
                    .clickable(enabled = canOpenItems && checkingCatKey.isEmpty()) { openCategory(key, catLabel) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(catLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A1A))
                    Text("$count items", fontSize = 11.sp, color = Color(0xFF5B5F6B))
                }
                Button(
                    onClick = { openCategory(key, catLabel) },
                    enabled = checkingCatKey.isEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isActiveShelf) Color(0xFF1F7A3D) else Color(0xFFD4A017)),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (checkingCatKey == key) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                    } else if (isActiveShelf) {
                        Text("▶ Viewing", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("🔒 Unlock", color = Color(0xFF12203D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // The shelf sits directly under whichever category it belongs to.
            if (isActiveShelf) {
                Spacer(Modifier.height(4.dp))
                VideoShelf(
                    items = unlockedItems,
                    playingIndex = playingIndex,
                    onSelect = { idx -> playingIndex = if (playingIndex == idx) null else idx }
                )
                Spacer(Modifier.height(14.dp))
            }
        }
        if (unlockMsg.isNotEmpty()) {
            Text(unlockMsg, fontSize = 11.5.sp, color = Color(0xFF946B00))
        }
    }

    if (showMobileDialog) {
        Dialog(onDismissRequest = { showMobileDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text("Apna Mobile Number Daalein", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                Spacer(Modifier.height(6.dp))
                Text("Jo number registration ke waqt diya tha, wahi daalein — isse aapki paid categories check hoti hain.", fontSize = 11.5.sp, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { if (it.length <= 10) mobile = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    placeholder = { Text("10-digit mobile number") }
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (mobile.length != 10) return@Button
                        saveMobile(context, mobile)
                        showMobileDialog = false
                        openCategory(pendingCatKey, pendingCatLabel)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("Continue", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showMobileDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun VideoShelf(items: List<LibraryItem>, playingIndex: Int?, onSelect: (Int) -> Unit) {
    if (items.isEmpty()) {
        Text("Abhi koi item nahi hai is category me.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
        return
    }

    // One horizontal row — students scroll sideways to browse, and whichever
    // card is tapped plays right there in the same card (no separate area).
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(items) { index, item ->
            VideoShelfCard(
                item = item,
                isPlaying = playingIndex == index,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun VideoShelfCard(item: LibraryItem, isPlaying: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val ytId = extractYouTubeId(item.url)

    Box(
        modifier = Modifier
            .width(300.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
    ) {
        if (isPlaying) {
            if (ytId != null) {
                key(ytId) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                loadUrl("$VIDEO_EMBED_HOST?id=$ytId")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶ Is link ko browser me kholein", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            // Small close button, top-right, over the playing video.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("✕ Close", color = Color(0xFFD4A017), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            if (ytId != null) {
                AsyncImage(
                    model = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clickable(onClick = onClick)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 60f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF0000))
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(videoGradient(item.title))
                        .clickable(onClick = onClick)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.date.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text("📅 ${item.date}", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                }
            }
        }
    }
}

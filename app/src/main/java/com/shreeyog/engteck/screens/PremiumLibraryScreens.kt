package com.shreeyog.engteck.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.google.firebase.database.FirebaseDatabase

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

    var checkingAccess by remember { mutableStateOf(false) }
    var viewingCatLabel by remember { mutableStateOf("") }
    var viewingItems by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }

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
        checkingAccess = true
        unlockMsg = ""
        FirebaseDatabase.getInstance().getReference("paidVideoCategories").child(currentMobile).child(catKey)
            .get()
            .addOnSuccessListener { snap ->
                val isPaid = snap.getValue(Boolean::class.java) ?: false
                if (!isPaid) {
                    checkingAccess = false
                    unlockMsg = "$catLabel abhi unlock nahi hai — payment ke baad access milega. Teacher se sampark karein."
                    return@addOnSuccessListener
                }
                FirebaseDatabase.getInstance().getReference(firebasePath).child(catKey).child(countChildPath)
                    .get()
                    .addOnSuccessListener { itemsSnap ->
                        checkingAccess = false
                        viewingItems = itemsSnap.children.map { c ->
                            LibraryItem(
                                title = c.child("title").getValue(String::class.java) ?: "Untitled",
                                url = c.child("url").getValue(String::class.java) ?: "",
                                date = c.child("date").getValue(String::class.java) ?: ""
                            )
                        }
                        viewingCatLabel = catLabel
                    }
                    .addOnFailureListener { checkingAccess = false; unlockMsg = "Load karne me dikkat aayi, dobara try karein." }
            }
            .addOnFailureListener { checkingAccess = false; unlockMsg = "Load karne me dikkat aayi, dobara try karein." }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .background(Color(0xFFF5F3EC), RoundedCornerShape(14.dp))
                    .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(14.dp))
                    .clickable(enabled = canOpenItems && !checkingAccess) { openCategory(key, catLabel) }
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
                    enabled = !checkingAccess,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017)),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (checkingAccess) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF12203D))
                    } else {
                        Text("🔒 Unlock", color = Color(0xFF12203D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
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

    if (viewingCatLabel.isNotEmpty()) {
        Dialog(onDismissRequest = { viewingCatLabel = "" }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text("$viewingCatLabel — $title", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                Spacer(Modifier.height(12.dp))
                if (viewingItems.isEmpty()) {
                    Text("Abhi koi item nahi hai is category me.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewingItems) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                    .clickable(enabled = item.url.isNotBlank()) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("▶ ${item.title}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                                    if (item.date.isNotEmpty()) Text(item.date, fontSize = 10.sp, color = Color(0xFF5B5F6B))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { viewingCatLabel = "" }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

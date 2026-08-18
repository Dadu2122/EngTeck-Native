package com.shreeyog.engteck.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// paidPdfLibrary/{catKey}/sets/{setKey} -> { title, questionsRaw } — same 7 categories as Admin.
private val STUDENT_PDF_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)
private val CORAL = Color(0xFFE85D4C)
private val GOLD = Color(0xFFD4A017)
private val NAVY = Color(0xFF12203D)

data class PremiumSetSummary(val key: String, val title: String)

@Composable
fun PremiumPdfLibraryCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("engteck_prefs", Context.MODE_PRIVATE) }

    var expanded by remember { mutableStateOf(true) }
    var setsByCat by remember { mutableStateOf<Map<String, List<PremiumSetSummary>>>(emptyMap()) }
    var unlockedCats by remember {
        mutableStateOf(
            STUDENT_PDF_CATS.map { it.first }.filter { prefs.getBoolean("sp_pdfcatpaid_$it", false) }.toSet()
        )
    }
    var unlockDialogCat by remember { mutableStateOf<String?>(null) }
    var openSet by remember { mutableStateOf<Triple<String, String, String>?>(null) } // catKey, setKey, title
    var openStudyMaterialCat by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("paidPdfLibrary")
            .get()
            .addOnSuccessListener { snapshot ->
                val result = mutableMapOf<String, List<PremiumSetSummary>>()
                for ((key, _) in STUDENT_PDF_CATS) {
                    result[key] = snapshot.child(key).child("sets").children.mapNotNull { child ->
                        val setKey = child.key ?: return@mapNotNull null
                        val title = child.child("title").getValue(String::class.java) ?: setKey
                        PremiumSetSummary(setKey, title)
                    }.sortedBy { it.key }
                }
                setsByCat = result
            }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text("PREMIUM", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = Color(0xFF1F9D55))
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(14.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PDF Library", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            Text(if (expanded) "▲" else "▼", color = CORAL, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (expanded) {
            Spacer(Modifier.height(16.dp))
            STUDENT_PDF_CATS.forEach { (catKey, label) ->
                val unlocked = unlockedCats.contains(catKey)
                val sets = setsByCat[catKey] ?: emptyList()

                Text(label, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(10.dp))

                if (unlocked) {
                    Text(
                        "👇 Click करके अंदर जाएं",
                        fontSize = 12.sp,
                        color = Color(0xFF5B5F6B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CORAL, RoundedCornerShape(14.dp))
                            .clickable { openStudyMaterialCat = catKey }
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            "📖 Enter Premium Study Material",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    sets.forEachIndexed { index, s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .background(Color.White, RoundedCornerShape(14.dp))
                                .border(1.5.dp, GOLD, RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(28.dp).background(NAVY, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${index + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                            }
                            Button(
                                onClick = { openSet = Triple(catKey, s.key, s.title) },
                                colors = ButtonDefaults.buttonColors(containerColor = CORAL),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Text("Read", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFCF3D9), RoundedCornerShape(100.dp))
                            .clickable { unlockDialogCat = catKey }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔒", fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${sets.size} premium PDF sets — Unlock this category",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF946B00)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    unlockDialogCat?.let { catKey ->
        val label = STUDENT_PDF_CATS.find { it.first == catKey }?.second ?: catKey
        CheckRegistrationAccessDialog(
            catKey = catKey,
            catLabel = label,
            savedMobile = prefs.getString("sp_mobile", "") ?: "",
            onDismiss = { unlockDialogCat = null },
            onUnlocked = { mobile ->
                prefs.edit().putString("sp_mobile", mobile).putBoolean("sp_pdfcatpaid_$catKey", true).apply()
                unlockedCats = unlockedCats + catKey
                unlockDialogCat = null
            }
        )
    }

    openSet?.let { (catKey, setKey, title) ->
        Dialog(onDismissRequest = { openSet = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                PremiumSetReaderScreen(catKey = catKey, setKey = setKey, setTitle = title, onBack = { openSet = null })
            }
        }
    }

    openStudyMaterialCat?.let { catKey ->
        val label = STUDENT_PDF_CATS.find { it.first == catKey }?.second ?: catKey
        val mobile = prefs.getString("sp_mobile", "") ?: ""
        Dialog(onDismissRequest = { openStudyMaterialCat = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                PremiumStudyMaterialScreen(catKey = catKey, catLabel = label, mobile = mobile, onExit = { openStudyMaterialCat = null })
            }
        }
    }
}

@Composable
private fun CheckRegistrationAccessDialog(
    catKey: String,
    catLabel: String,
    savedMobile: String,
    onDismiss: () -> Unit,
    onUnlocked: (String) -> Unit
) {
    var mobile by remember { mutableStateOf(savedMobile) }
    var checking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text("Unlock $catLabel PDF Library", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(6.dp))
            Text(
                "$catLabel ke liye koi bhi paid plan (Live ya No Live Class) active ho, to yahan sirf apna registered mobile number daalo.",
                fontSize = 11.5.sp,
                color = Color(0xFF5B5F6B),
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                placeholder = { Text("10-digit registered mobile number") }
            )
            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(errorMsg, fontSize = 11.5.sp, color = Color(0xFFC0392B))
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (mobile.length != 10) {
                        errorMsg = "Sahi 10-digit mobile number daalo."
                        return@Button
                    }
                    checking = true
                    errorMsg = ""
                    val todayISO = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
                    FirebaseDatabase.getInstance().getReference("registrations")
                        .orderByChild("mobile").equalTo(mobile)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            checking = false
                            var hasActivePlan = false
                            for (child in snapshot.children) {
                                val planCategory = child.child("planCategory").getValue(String::class.java)
                                val planExpiryISO = child.child("planExpiryISO").getValue(String::class.java)
                                if (planCategory == catKey && planExpiryISO != null && planExpiryISO >= todayISO) {
                                    hasActivePlan = true
                                }
                            }
                            if (hasActivePlan) {
                                onUnlocked(mobile)
                            } else if (!snapshot.exists()) {
                                errorMsg = "Ye mobile number registered nahi hai. Pehle Registration form bharo."
                            } else {
                                errorMsg = "Is number ka $catLabel plan active nahi hai (expire ho chuka hai ya category match nahi). Registration form se pay/renew karo."
                            }
                        }
                        .addOnFailureListener {
                            checking = false
                            errorMsg = "Check karne mein dikkat aayi. Dobara try karo."
                        }
                },
                enabled = !checking,
                colors = ButtonDefaults.buttonColors(containerColor = GOLD, contentColor = NAVY),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(if (checking) "Checking..." else "Check Access", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}


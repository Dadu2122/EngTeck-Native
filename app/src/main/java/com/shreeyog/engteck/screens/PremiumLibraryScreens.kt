package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

private val PREMIUM_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)

@Composable
fun VideoLibraryCard() {
    PremiumLibraryShell(
        firebasePath = "videoLibrary",
        label = "PREMIUM",
        icon = "🎬",
        title = "Video Library",
        countChildPath = "videos"
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
        countChildPath = "recordings"
    )
}

@Composable
fun PaidPdfLibraryCard() {
    PremiumLibraryShell(
        firebasePath = "paidPdfLibrary",
        label = "PREMIUM",
        icon = "📚",
        title = "PDF Library",
        countChildPath = "sets"
    )
}

@Composable
private fun PremiumLibraryShell(
    firebasePath: String,
    label: String,
    icon: String,
    title: String,
    countChildPath: String
) {
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var unlockMsg by remember { mutableStateOf("") }

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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(catLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A1A))
                    Text("$count items", fontSize = 11.sp, color = Color(0xFF5B5F6B))
                }
                Button(
                    onClick = { unlockMsg = "$catLabel unlock karne ke liye payment system jald hi native app me aayega" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017)),
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("🔒 Unlock", color = Color(0xFF12203D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (unlockMsg.isNotEmpty()) {
            Text(unlockMsg, fontSize = 11.5.sp, color = Color(0xFF946B00))
        }
    }
}

package com.shreeyog.engteck.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.ui.theme.Gold
import com.shreeyog.engteck.ui.theme.InkSoft
import com.shreeyog.engteck.ui.theme.NavyDeep

data class StudySetItem(val key: String, val title: String)

// Only these categories have a Daily Practice set on the backend (matches
// admin's DP_CATS) — "grammar" doesn't, so its card is skipped there.
private val DAILY_PRACTICE_CATS = setOf("tgt", "pgt", "lt", "gic", "upessc", "uphesc", "net")

@Composable
fun StudySetsScreen(
    catKey: String,
    catLabel: String,
    onSetClick: (setKey: String, setTitle: String) -> Unit,
    onDailyPracticeClick: () -> Unit = {}
) {
    var loading by remember { mutableStateOf(true) }
    var sets by remember { mutableStateOf<List<StudySetItem>>(emptyList()) }

    LaunchedEffect(catKey) {
        FirebaseDatabase.getInstance().getReference("studySets").child(catKey).child("sets")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                val list = snapshot.children.mapNotNull { child ->
                    val title = child.child("title").getValue(String::class.java)
                    if (title != null) StudySetItem(child.key ?: "", title) else null
                }.sortedBy { it.key }
                sets = list
            }
            .addOnFailureListener {
                loading = false
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(catLabel, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(4.dp))
        Text("Select a set", fontSize = 13.sp, color = InkSoft)
        Spacer(Modifier.height(16.dp))

        if (catKey in DAILY_PRACTICE_CATS) {
            Card(
                onClick = onDailyPracticeClick,
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFFFDF6)),
                border = BorderStroke(1.5.dp, Gold),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🔥 Daily Practice", color = NavyDeep, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("225 fresh questions every day", color = InkSoft, fontSize = 11.sp)
                    }
                    Text("›", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyDeep)
            }
        } else if (sets.isEmpty()) {
            Text("No sets available yet.", color = InkSoft, fontSize = 14.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sets) { set ->
                    Card(
                        onClick = { onSetClick(set.key, set.title) },
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(set.title, color = NavyDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("›", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudyTabRoot() {
    var selectedCat by remember { mutableStateOf<StudyCategory?>(null) }
    var selectedSet by remember { mutableStateOf<StudySetItem?>(null) }
    var showDailyPracticeHub by remember { mutableStateOf(false) }
    var dpViewerPart by remember { mutableStateOf<Pair<String, String>?>(null) } // partKey, partLabel
    var showMixedTest by remember { mutableStateOf(false) }

    when {
        selectedCat == null -> {
            StudyScreen(onCategoryClick = { key, label ->
                selectedCat = StudyCategory(key, label)
            })
        }
        showMixedTest -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { showMixedTest = false }) { Text("‹ Back") }
                MixedTestScreen(catKey = selectedCat!!.key)
            }
        }
        dpViewerPart != null -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { dpViewerPart = null }) { Text("‹ Back") }
                DailyPracticeViewerScreen(
                    catKey = selectedCat!!.key,
                    partKey = dpViewerPart!!.first,
                    partLabel = dpViewerPart!!.second
                )
            }
        }
        showDailyPracticeHub -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { showDailyPracticeHub = false }) { Text("‹ Back") }
                DailyPracticeHubScreen(
                    catKey = selectedCat!!.key,
                    catLabel = selectedCat!!.label,
                    onPartClick = { partKey, partLabel, isMixed ->
                        if (isMixed) showMixedTest = true else dpViewerPart = partKey to partLabel
                    }
                )
            }
        }
        selectedSet == null -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { selectedCat = null }) { Text("‹ Back") }
                StudySetsScreen(
                    catKey = selectedCat!!.key,
                    catLabel = selectedCat!!.label,
                    onSetClick = { key, title -> selectedSet = StudySetItem(key, title) },
                    onDailyPracticeClick = { showDailyPracticeHub = true }
                )
            }
        }
        else -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { selectedSet = null }) { Text("‹ Back") }
                SetDetailScreen(
                    catKey = selectedCat!!.key,
                    setKey = selectedSet!!.key,
                    setTitle = selectedSet!!.title
                )
            }
        }
    }
}

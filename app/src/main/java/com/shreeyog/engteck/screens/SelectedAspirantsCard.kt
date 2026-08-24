package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay

data class MeritCat(val key: String, val label: String, val count: Int)

private val MERIT_CAT_LABELS = mapOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT Grade", "gic" to "GIC Lecturer"
)

@Composable
fun SelectedAspirantsCard() {
    var cats by remember { mutableStateOf<List<MeritCat>>(emptyList()) }
    var cardVisible by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }

    var tapCount by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var selectedCatKey by remember { mutableStateOf("tgt") }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("content").child("showSelectedAspirantsCard")
            .get()
            .addOnSuccessListener { snapshot ->
                // Defaults to visible if the flag has never been set
                cardVisible = snapshot.getValue(Boolean::class.java) ?: true
            }
    }

    LaunchedEffect(refreshKey) {
        FirebaseDatabase.getInstance().getReference("selectedAspirants")
            .get()
            .addOnSuccessListener { snapshot ->
                cats = listOf("tgt", "pgt", "lt", "gic").map { key ->
                    val count = snapshot.child(key).childrenCount.toInt()
                    MeritCat(key, MERIT_CAT_LABELS[key] ?: key, count)
                }
            }
    }

    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(1500)
            tapCount = 0
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Selected Aspirant") },
            text = {
                Column {
                    Text("Category", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MERIT_CAT_LABELS.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedCatKey == key,
                                onClick = { selectedCatKey = key },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Aspirant Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = nameInput.trim()
                    if (trimmed.isNotBlank()) {
                        FirebaseDatabase.getInstance().getReference("selectedAspirants")
                            .child(selectedCatKey)
                            .push()
                            .setValue(trimmed)
                        nameInput = ""
                        refreshKey++
                    }
                    showAddDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (!cardVisible) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12203D), RoundedCornerShape(20.dp))
                .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(20.dp))
                .padding(vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "RESULTS",
                    color = Color(0xFFF0D384),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Selected Aspirants",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            tapCount++
                            if (tapCount >= 4) {
                                tapCount = 0
                                showAddDialog = true
                            }
                        }
                )
            }
            Spacer(Modifier.height(16.dp))

            if (cats.isNotEmpty()) {
                // Horizontal swipeable row — contentPadding on the end lets the next
                // card "peek" in from the right edge as a swipe hint, matching how
                // Instagram-style story rows signal there's more to scroll.
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 18.dp, end = 40.dp)
                ) {
                    items(cats) { cat ->
                        MeritCatCard(cat)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeritCatCard(cat: MeritCat) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(14.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("${cat.count}", color = Color(0xFFD4A017), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(cat.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(1.dp))
        Text("SELECTED", color = Color.White.copy(alpha = 0.55f), fontSize = 10.5.sp, letterSpacing = 0.5.sp)
    }
}

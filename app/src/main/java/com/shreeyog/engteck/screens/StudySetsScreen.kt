package com.shreeyog.engteck.screens

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

@Composable
fun StudySetsScreen(
    catKey: String,
    catLabel: String,
    onSetClick: (setKey: String, setTitle: String) -> Unit
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
        Text("Set chunein", fontSize = 13.sp, color = InkSoft)
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyDeep)
            }
        } else if (sets.isEmpty()) {
            Text("Abhi koi set available nahi hai.", color = InkSoft, fontSize = 14.sp)
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

    when {
        selectedCat == null -> {
            StudyScreen(onCategoryClick = { key, label ->
                selectedCat = StudyCategory(key, label)
            })
        }
        selectedSet == null -> {
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick = { selectedCat = null }) { Text("‹ Back") }
                StudySetsScreen(
                    catKey = selectedCat!!.key,
                    catLabel = selectedCat!!.label,
                    onSetClick = { key, title -> selectedSet = StudySetItem(key, title) }
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

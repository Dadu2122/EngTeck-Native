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

data class StudyCategory(val key: String, val label: String)

private val CATEGORY_ORDER = listOf(
    StudyCategory("grammar", "Grammar"),
    StudyCategory("tgt", "TGT"),
    StudyCategory("pgt", "PGT"),
    StudyCategory("lt", "LT Grade"),
    StudyCategory("gic", "GIC Lecturer"),
    StudyCategory("upessc", "UPESSC"),
    StudyCategory("uphesc", "UPHESC"),
    StudyCategory("net", "NET")
)

@Composable
fun StudyScreen(onCategoryClick: (catKey: String, label: String) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var availableKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("studySets")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                availableKeys = snapshot.children.mapNotNull { it.key }.toSet()
            }
            .addOnFailureListener {
                loading = false
                availableKeys = CATEGORY_ORDER.map { it.key }.toSet()
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Study Material", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(4.dp))
        Text("Select your category", fontSize = 13.sp, color = InkSoft)
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyDeep)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CATEGORY_ORDER.filter { availableKeys.contains(it.key) }) { cat ->
                    Card(
                        onClick = { onCategoryClick(cat.key, cat.label) },
                        colors = CardDefaults.cardColors(containerColor = NavyDeep),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.label, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("›", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

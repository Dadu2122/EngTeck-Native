import androidx.compose.ui.draw.clip
package com.shreeyog.engteck.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.google.firebase.database.FirebaseDatabase

private val CAT_TABS = listOf("tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC")
private val CUTOFF_COLS = listOf("general" to "Gen", "obc" to "OBC", "sc" to "SC", "st" to "ST", "ews" to "EWS", "pwd" to "PWD")

data class QpEntry(val title: String, val link: String)

@Composable
fun QuestionPapersCard() {
    var activeCat by remember { mutableStateOf("tgt") }
    var data by remember { mutableStateOf<Map<String, Map<String, List<QpEntry>>>>(emptyMap()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("questionPapers")
            .get()
            .addOnSuccessListener { snapshot ->
                val result = mutableMapOf<String, Map<String, List<QpEntry>>>()
                for (catSnap in snapshot.children) {
                    val years = mutableMapOf<String, List<QpEntry>>()
                    for (yearSnap in catSnap.children) {
                        val papers = yearSnap.children.mapNotNull { p ->
                            val title = p.child("title").getValue(String::class.java) ?: "Question Paper"
                            val link = p.child("link").getValue(String::class.java) ?: ""
                            if (link.isNotEmpty()) QpEntry(title, link) else null
                        }
                        years[yearSnap.key ?: ""] = papers
                    }
                    result[catSnap.key ?: ""] = years
                }
                data = result
            }
    }

    SectionShell(icon = "📄", title = "Official Question Papers") {
        CatTabs(activeCat) { activeCat = it }
        Spacer(Modifier.height(10.dp))
        val years = (data[activeCat] ?: emptyMap()).keys.sortedDescending()
        if (years.isEmpty()) {
            EmptyNote("अभी कोई paper upload नहीं हुआ इस category में।")
        } else {
            years.forEach { year ->
                var expanded by remember(year, activeCat) { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0xFFF5F3EC), RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color(0xFFE3DFD3), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(year, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF1A1A1A))
                        Text(if (expanded) "▲" else "▼", color = Color(0xFFD4A017))
                    }
                    if (expanded) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            val papers = data[activeCat]?.get(year) ?: emptyList()
                            if (papers.isEmpty()) {
                                EmptyNote("इस साल के लिए अभी कोई paper नहीं।")
                            } else {
                                papers.forEach { paper ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .background(Color.White, RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(10.dp))
                                            .clickable {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paper.link)))
                                                } catch (e: Exception) { }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(paper.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                                        Text("⬇", color = Color(0xFFD4A017))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfficialCutoffsCard() {
    CutoffCardBase(firebasePath = "officialCutoffs", icon = "📊", title = "Official Cut-offs")
}

@Composable
fun ExpectedCutoffsCard() {
    CutoffCardBase(firebasePath = "expectedCutoffs", icon = "📈", title = "Expected Cut-offs", badge = "✨ Teacher's Estimate — Not Official")
}

@Composable
private fun CutoffCardBase(firebasePath: String, icon: String, title: String, badge: String? = null) {
    var activeCat by remember { mutableStateOf("tgt") }
    var data by remember { mutableStateOf<Map<String, Map<String, Map<String, String>>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference(firebasePath)
            .get()
            .addOnSuccessListener { snapshot ->
                val result = mutableMapOf<String, Map<String, Map<String, String>>>()
                for (catSnap in snapshot.children) {
                    val years = mutableMapOf<String, Map<String, String>>()
                    for (yearSnap in catSnap.children) {
                        val row = CUTOFF_COLS.associate { (key, _) ->
                            key to (yearSnap.child(key).getValue(String::class.java) ?: "—")
                        }
                        years[yearSnap.key ?: ""] = row
                    }
                    result[catSnap.key ?: ""] = years
                }
                data = result
            }
    }

    SectionShell(icon = icon, title = title) {
        badge?.let {
            Box(
                modifier = Modifier
                    .background(Color(0xFFD4A017).copy(alpha = 0.15f), RoundedCornerShape(100.dp))
                    .border(1.dp, Color(0xFFD4A017), RoundedCornerShape(100.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(it, color = Color(0xFF946B00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
        }
        CatTabs(activeCat) { activeCat = it }
        Spacer(Modifier.height(10.dp))
        val years = (data[activeCat] ?: emptyMap()).keys.sortedDescending()
        if (years.isEmpty()) {
            EmptyNote("अभी data नहीं भरा गया इस category में।")
        } else {
            years.forEach { year ->
                val row = data[activeCat]?.get(year) ?: emptyMap()
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    Text(year, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1A1A1A))
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF12203D))) {
                            CUTOFF_COLS.forEach { (_, label) ->
                                Text(
                                    label,
                                    color = Color(0xFFF0D384),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F3EC))) {
                            CUTOFF_COLS.forEach { (key, _) ->
                                Text(
                                    row[key] ?: "—",
                                    color = Color(0xFF1A1A1A),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionShell(icon: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text(
            "REFERENCE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Color(0xFF1B6B79)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "$icon $title",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun CatTabs(active: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        CAT_TABS.forEach { (key, label) ->
            val isActive = active == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (isActive) Color(0xFF1B6B79) else Color(0xFFF5F3EC))
                    .border(1.5.dp, if (isActive) Color(0xFF1B6B79) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                    .clickable { onSelect(key) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (isActive) Color.White else Color(0xFF5B5F6B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, fontSize = 12.sp, color = Color(0xFF5B5F6B), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
}

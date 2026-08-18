package com.shreeyog.engteck.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.FirebaseDatabase

// premiumContent/{catKey}/topicSections/{sectionKey}/points/{pointKey} -> { title, content, order, group? }
private val TSA_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)
data class TopicSectionDef(val key: String, val label: String)
private val TSA_SECTION_DEFS = listOf(
    TopicSectionDef("historyOfEnglishLiterature", "History of English Literature"),
    TopicSectionDef("formsOfLiterature", "Forms of Literature"),
    TopicSectionDef("literaryDevices", "Literary Term / Device"),
    TopicSectionDef("figuresOfSpeech", "Figure of Speech"),
    TopicSectionDef("literaryTheories", "Literary Theories"),
    TopicSectionDef("literaryMovements", "Literary Movements"),
    TopicSectionDef("grammar", "Grammar Section")
)
// Forms of Literature is the one section that splits into named sub-groups.
private val TSA_SECTION_GROUPS = mapOf(
    "formsOfLiterature" to listOf("Poetry", "Prose", "Drama", "Cross-Genre / Mixed Forms")
)

data class TopicPointEntry(val key: String, val title: String, val content: String, val group: String?)

@Composable
fun AdminTopicSectionsCard() {
    var activeCat by remember { mutableStateOf("tgt") }
    var pointCounts by remember(activeCat) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var refreshTick by remember { mutableStateOf(0) }
    var manageSection by remember { mutableStateOf<TopicSectionDef?>(null) }

    LaunchedEffect(activeCat, refreshTick) {
        FirebaseDatabase.getInstance().getReference("premiumContent").child(activeCat).child("topicSections")
            .get()
            .addOnSuccessListener { snapshot ->
                val result = mutableMapOf<String, Int>()
                for (sec in TSA_SECTION_DEFS) {
                    result[sec.key] = snapshot.child(sec.key).child("points").childrenCount.toInt()
                }
                pointCounts = result
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Topic Sections", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(4.dp))
        Text(
            "History, Forms of Literature, Devices, Figures of Speech, Theories, Movements, Grammar",
            fontSize = 11.sp,
            color = Color(0xFF5B5F6B)
        )
        Spacer(Modifier.height(12.dp))

        TSA_CATS.chunked(4).forEach { rowCats ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                rowCats.forEach { (key, label) ->
                    val active = activeCat == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC), RoundedCornerShape(100.dp))
                            .border(1.5.dp, if (active) Color(0xFF1B6B79) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                            .clickable { activeCat = key }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                repeat(4 - rowCats.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))

        TSA_SECTION_DEFS.forEach { sec ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sec.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                TextButton(onClick = { manageSection = sec }) {
                    Text("Manage (${pointCounts[sec.key] ?: 0})", fontSize = 11.sp)
                }
            }
        }
    }

    manageSection?.let { sec ->
        ManageTopicPointsDialog(
            catKey = activeCat,
            section = sec,
            onDismiss = { manageSection = null; refreshTick++ }
        )
    }
}

@Composable
private fun ManageTopicPointsDialog(catKey: String, section: TopicSectionDef, onDismiss: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var points by remember { mutableStateOf<List<TopicPointEntry>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<TopicPointEntry?>(null) }
    val groups = TSA_SECTION_GROUPS[section.key]

    LaunchedEffect(refreshTick) {
        loading = true
        FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
            .child("topicSections").child(section.key).child("points")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                points = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val title = child.child("title").getValue(String::class.java) ?: ""
                    val content = child.child("content").getValue(String::class.java) ?: ""
                    val group = child.child("group").getValue(String::class.java)
                    TopicPointEntry(key, title, content, group)
                }
            }
            .addOnFailureListener { loading = false }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(section.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(12.dp))

            if (loading) {
                CircularProgressIndicator(color = Color(0xFF12203D))
            } else {
                if (points.isEmpty()) {
                    Text("No points added yet.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                    Spacer(Modifier.height(10.dp))
                } else if (groups != null) {
                    val buckets = groups.map { g -> g to points.filter { it.group == g } }
                    val ungrouped = points.filter { it.group == null || it.group !in groups }
                    val allBuckets = buckets + (if (ungrouped.isNotEmpty()) listOf("Ungrouped" to ungrouped) else emptyList())
                    Column(modifier = Modifier.heightIn(max = 360.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            allBuckets.forEach { (g, pts) ->
                                item {
                                    Text(
                                        "$g (${pts.size})",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B6B79),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                items(pts) { p ->
                                    TopicPointRow(p) { editTarget = p }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else {
                    Column(modifier = Modifier.heightIn(max = 360.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(points) { p ->
                                TopicPointRow(p) { editTarget = p }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick = { addOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("+ Add Point(s)", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }

    if (addOpen) {
        AddTopicPointsDialog(
            catKey = catKey,
            section = section,
            groups = groups,
            onDismiss = { addOpen = false },
            onSaved = { addOpen = false; refreshTick++ }
        )
    }
    editTarget?.let { p ->
        EditTopicPointDialog(
            catKey = catKey,
            section = section,
            point = p,
            groups = groups,
            onDismiss = { editTarget = null; refreshTick++ }
        )
    }
}

@Composable
private fun TopicPointRow(p: TopicPointEntry, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
            .clickable { onEdit() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(p.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
            Text(
                if (p.content.isBlank()) "No content yet" else "${p.content.take(60)}${if (p.content.length > 60) "…" else ""}",
                fontSize = 10.5.sp,
                color = Color(0xFF5B5F6B)
            )
        }
        TextButton(onClick = onEdit) { Text("Edit") }
    }
}

@Composable
private fun AddTopicPointsDialog(catKey: String, section: TopicSectionDef, groups: List<String>?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var titles by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf(groups?.firstOrNull() ?: "") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text("Add Point(s) — ${section.label}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(10.dp))

            if (groups != null) {
                Text("Category", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { g ->
                        val active = selectedGroup == g
                        Box(
                            modifier = Modifier
                                .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC), RoundedCornerShape(100.dp))
                                .border(1.dp, if (active) Color(0xFF1B6B79) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                                .clickable { selectedGroup = g }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(g, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text("Point Title(s) — one per line", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = titles,
                onValueChange = { titles = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("Metaphor\nSimile\nSymbolism") }
            )

            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(errorMsg, fontSize = 11.sp, color = Color(0xFFC0392B))
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val titleList = titles.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (titleList.isEmpty()) { errorMsg = "At least one title is required."; return@Button }
                    saving = true
                    errorMsg = ""
                    val ref = FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
                        .child("topicSections").child(section.key).child("points")
                    val updates = mutableMapOf<String, Any>()
                    titleList.forEach { t ->
                        val key = ref.push().key
                        if (key != null) {
                            val point = mutableMapOf<String, Any>("title" to t, "content" to "")
                            if (groups != null && selectedGroup.isNotEmpty()) point["group"] = selectedGroup
                            updates[key] = point
                        }
                    }
                    ref.updateChildren(updates)
                        .addOnSuccessListener { saving = false; onSaved() }
                        .addOnFailureListener { saving = false; errorMsg = "Failed to save." }
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(if (saving) "Saving..." else "Add", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
private fun EditTopicPointDialog(catKey: String, section: TopicSectionDef, point: TopicPointEntry, groups: List<String>?, onDismiss: () -> Unit) {
    var content by remember { mutableStateOf(point.content) }
    var selectedGroup by remember { mutableStateOf(point.group ?: groups?.firstOrNull() ?: "") }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(point.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(10.dp))

            if (groups != null) {
                Text("Category", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { g ->
                        val active = selectedGroup == g
                        Box(
                            modifier = Modifier
                                .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC), RoundedCornerShape(100.dp))
                                .border(1.dp, if (active) Color(0xFF1B6B79) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                                .clickable { selectedGroup = g }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(g, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text("Content", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 220.dp),
                shape = RoundedCornerShape(10.dp),
                placeholder = { Text("Origin, explanation, examples — jitna chaho likho…") }
            )

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(status, fontSize = 11.sp, color = Color(0xFF1F7A3D))
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        saving = true
                        val ref = FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
                            .child("topicSections").child(section.key).child("points").child(point.key)
                        val updates = mutableMapOf<String, Any>("content" to content)
                        if (groups != null) updates["group"] = selectedGroup
                        ref.updateChildren(updates)
                            .addOnSuccessListener { saving = false; status = "Saved ✓" }
                            .addOnFailureListener { saving = false; status = "Failed to save" }
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
                        FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
                            .child("topicSections").child(section.key).child("points").child(point.key)
                            .removeValue()
                            .addOnSuccessListener { deleting = false; onDismiss() }
                            .addOnFailureListener { deleting = false; status = "Failed to delete" }
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

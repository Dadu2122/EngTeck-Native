package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

// premiumContent/{catKey}/writers/{writerKey} -> { name, biography, criticalComments, works: {...} }
// works/{workKey} -> { title, summary, characters, lines, themes, questions }
private val WRITER_CATS = listOf(
    "tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC Lecturer",
    "upessc" to "UPESSC", "uphesc" to "UPHESC", "net" to "NET"
)

data class WriterEntry(val key: String, val name: String, val workCount: Int)
data class WorkEntry(val key: String, val title: String, val type: String)

// Same work-type sections as the web app's Manage Works panel — works are grouped under
// these headers (Main Novels, Minor Novels, Novels, Short Story Collections, Collections,
// Drama, Poems, Sonnets, Non-Fiction, Essays) in this fixed display order.
data class WorkTypeSection(val key: String, val label: String)
val WORK_TYPE_SECTIONS = listOf(
    WorkTypeSection("mainNovel", "Main Novels"),
    WorkTypeSection("minorNovel", "Minor Novels"),
    WorkTypeSection("novel", "Novels"),
    WorkTypeSection("shortStory", "Short Story Collections"),
    WorkTypeSection("collection", "Collections"),
    WorkTypeSection("drama", "Drama"),
    WorkTypeSection("individual", "Poems"),
    WorkTypeSection("sonnet", "Sonnets"),
    WorkTypeSection("prose", "Non-Fiction"),
    WorkTypeSection("essay", "Essays")
)
fun workTypeLabel(key: String): String = WORK_TYPE_SECTIONS.find { it.key == key }?.label ?: "Poems"

// ---------- Auto-classification of a work's category from its title, ported from the web app's
// guessWorkType() so the native admin panel guesses the same category (Drama/Novel/Sonnet/etc.)
// for known Shakespeare/Milton/Wordsworth/Keats/Galsworthy/Orwell works. Admin can still override
// via the chip selector; this only sets a smart default. ----------
private val KNOWN_DRAMA_TITLES = setOf(
    "henry vi, part 1", "henry vi, part 2", "henry vi, part 3", "richard iii", "king john",
    "richard ii", "henry iv, part 1", "henry iv, part 2", "henry v", "henry viii",
    "the comedy of errors", "the taming of the shrew", "the two gentlemen of verona",
    "love's labour's lost", "a midsummer night's dream", "the merchant of venice",
    "the merry wives of windsor", "much ado about nothing", "as you like it", "twelfth night",
    "troilus and cressida", "all's well that ends well", "measure for measure",
    "titus andronicus", "romeo and juliet", "julius caesar", "hamlet", "othello", "king lear",
    "macbeth", "antony and cleopatra", "coriolanus", "timon of athens", "pericles", "cymbeline",
    "the winter's tale", "the tempest",
    "the borderers", // Wordsworth's one play
    "otho the great", "king stephen", // Keats' dramas
    "comus", "samson agonistes" // Milton's dramas
)
private val KNOWN_COLLECTION_TITLES = setOf(
    "an evening walk", "descriptive sketches", "lyrical ballads", "lyrical ballads, 2nd edition",
    "poems, in two volumes", "the excursion", "poems (collected edition)",
    "the white doe of rylstone", "peter bell", "the waggoner", "ecclesiastical sketches", "the prelude",
    "poems", "endymion", "endymion: a poetic romance",
    "lamia, isabella, the eve of st. agnes, and other poems",
    "poems of mr. john milton, both english and latin", "paradise lost", "paradise regained"
)
private val KNOWN_PROSE_TITLES = setOf(
    "the convention of cintra", "guide to the lakes",
    "of reformation touching church-discipline in england", "of prelatical episcopacy",
    "animadversions upon the remonstrant's defence", "the reason of church-government urged against prelaty",
    "an apology for smectymnuus", "the doctrine and discipline of divorce",
    "the judgement of martin bucer concerning divorce", "of education", "areopagitica",
    "tetrachordon", "colasterion", "the tenure of kings and magistrates", "eikonoklastes",
    "a treatise of civil power in ecclesiastical causes",
    "considerations touching the likeliest means to remove hirelings",
    "the ready and easy way to establish a free commonwealth", "a history of britain",
    "of true religion, heresy, schism, toleration",
    "defensio pro populo anglicano", "defensio secunda", "pro se defensio",
    "artis logicae", "de doctrina christiana",
    "down and out in paris and london", "the road to wigan pier", "homage to catalonia"
)
private val KNOWN_NOVEL_TITLES = setOf(
    "burmese days", "a clergyman's daughter", "keep the aspidistra flying",
    "coming up for air", "animal farm", "nineteen eighty-four", "1984"
)
private val KNOWN_ESSAY_TITLES = setOf(
    "a hanging", "shooting an elephant", "charles dickens", "boys' weeklies",
    "inside the whale", "the lion and the unicorn: socialism and the english genius",
    "my country right or left", "marrakech", "politics and the english language",
    "why i write", "some thoughts on the common toad", "decline of the english murder",
    "politics vs. literature: an examination of gulliver's travels", "how the poor die",
    "notes on nationalism", "such, such were the joys", "reflections on gandhi",
    "a nice cup of tea", "confessions of a book reviewer", "books v. cigarettes",
    "england your england", "the prevention of literature", "in front of your nose",
    "rudyard kipling", "wells, hitler and the world state",
    "arthur koestler", "raffles and miss blandish", "antisemitism in britain",
    "you and the atomic bomb", "nonsense poetry", "poetry and the microphone",
    "the sporting spirit", "pleasure spots", "a good word for the vicar of bray",
    "lear, tolstoy and the fool", "writers and leviathan", "second thoughts on james burnham",
    "bookshop memories", "spilling the spanish beans"
)
private val KNOWN_MAIN_NOVEL_TITLES = setOf(
    "the forsyte saga", "the man of property", "in chancery", "awakening", "to let",
    "indian summer of a forsyte", "a modern comedy", "the white monkey", "the silver spoon",
    "a silent wooing", "swan song", "end of the chapter", "maid in waiting",
    "flowering wilderness", "over the river", "the country house"
)
private val KNOWN_MINOR_NOVEL_TITLES = setOf(
    "jocelyn", "villa rubein", "the island pharisees", "fraternity", "the patrician",
    "the dark flower", "the freelands", "beyond", "saint's progress", "the burning spear",
    "passers by"
)
private val KNOWN_SHORT_STORY_TITLES = setOf(
    "from the four winds", "a commentary", "a motley", "the inn of tranquillity",
    "memories", "the little man and other satires", "five tales", "tatterdemalion",
    "captures", "on forsyte 'change", "stories from 'forsytes, pendyces and others'"
)
private fun baseWorkTitle(title: String): String {
    return title
        .substringBefore("—")
        .replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
        .trim().lowercase()
}
fun guessWorkType(title: String): String {
    val t = baseWorkTitle(title)
    if (t.isBlank()) return "individual"
    if (Regex("^sonnet\\b").containsMatchIn(t)) return "sonnet"
    if (KNOWN_DRAMA_TITLES.contains(t)) return "drama"
    if (KNOWN_MAIN_NOVEL_TITLES.contains(t)) return "mainNovel"
    if (KNOWN_MINOR_NOVEL_TITLES.contains(t)) return "minorNovel"
    if (KNOWN_NOVEL_TITLES.contains(t)) return "novel"
    if (KNOWN_SHORT_STORY_TITLES.contains(t)) return "shortStory"
    if (KNOWN_ESSAY_TITLES.contains(t)) return "essay"
    if (KNOWN_COLLECTION_TITLES.contains(t)) return "collection"
    if (KNOWN_PROSE_TITLES.contains(t)) return "prose"
    return "individual"
}

@Composable
fun AdminWritersCard() {
    var activeCat by remember { mutableStateOf("tgt") }
    var loading by remember(activeCat) { mutableStateOf(true) }
    var writers by remember(activeCat) { mutableStateOf<List<WriterEntry>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }

    var addWriterOpen by remember { mutableStateOf(false) }
    var bioTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // writerKey, writerName
    var criticalTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var worksTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(activeCat, refreshTick) {
        loading = true
        FirebaseDatabase.getInstance().getReference("premiumContent").child(activeCat).child("writers")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                writers = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val name = child.child("name").getValue(String::class.java) ?: "Untitled"
                    val workCount = child.child("works").childrenCount.toInt()
                    WriterEntry(key, name, workCount)
                }
            }
            .addOnFailureListener { loading = false }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Writers & Works", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(4.dp))
        Text(
            "Biography, Critical Comments & Works — Premium Study Material",
            fontSize = 11.sp,
            color = Color(0xFF5B5F6B)
        )
        Spacer(Modifier.height(12.dp))

        WRITER_CATS.chunked(4).forEach { rowCats ->
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
        Spacer(Modifier.height(8.dp))

        if (loading) {
            CircularProgressIndicator(color = Color(0xFF12203D))
        } else {
            if (writers.isEmpty()) {
                Text("No writers added yet in this category.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(10.dp))
            } else {
                Column(modifier = Modifier.heightIn(max = 420.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(writers) { w ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F3EC), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(w.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                                Text("${w.workCount} works", fontSize = 10.5.sp, color = Color(0xFF5B5F6B))
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AdminSmallBtn("✏️ Bio") { bioTarget = w.key to w.name }
                                    AdminSmallBtn("💬 Critical") { criticalTarget = w.key to w.name }
                                    AdminSmallBtn("📚 Works") { worksTarget = w.key to w.name }
                                }
                                Spacer(Modifier.height(6.dp))
                                TextButton(onClick = {
                                    FirebaseDatabase.getInstance().getReference("premiumContent").child(activeCat)
                                        .child("writers").child(w.key).removeValue()
                                        .addOnSuccessListener { refreshTick++ }
                                }) {
                                    Text("✕ Delete Writer", color = Color(0xFFC0392B), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Button(
                onClick = { addWriterOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("+ Add Writer", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (addWriterOpen) {
        AddWriterDialog(
            catKey = activeCat,
            onDismiss = { addWriterOpen = false },
            onSaved = { addWriterOpen = false; refreshTick++ }
        )
    }
    bioTarget?.let { (writerKey, writerName) ->
        WriterTextFieldDialog(
            catKey = activeCat,
            writerKey = writerKey,
            firebaseField = "biography",
            title = "$writerName — Biography",
            onDismiss = { bioTarget = null }
        )
    }
    criticalTarget?.let { (writerKey, writerName) ->
        WriterTextFieldDialog(
            catKey = activeCat,
            writerKey = writerKey,
            firebaseField = "criticalComments",
            title = "$writerName — Critical Comments",
            onDismiss = { criticalTarget = null }
        )
    }
    worksTarget?.let { (writerKey, writerName) ->
        WorksListDialog(
            catKey = activeCat,
            writerKey = writerKey,
            writerName = writerName,
            onDismiss = { worksTarget = null; refreshTick++ }
        )
    }
}

@Composable
private fun AdminSmallBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
    }
}

@Composable
private fun AddWriterDialog(catKey: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text("Add Writer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                placeholder = { Text("Writer's full name") }
            )
            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(errorMsg, fontSize = 11.sp, color = Color(0xFFC0392B))
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (name.isBlank()) { errorMsg = "Name is required."; return@Button }
                    saving = true
                    val ref = FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey).child("writers")
                    val key = ref.push().key
                    if (key == null) { saving = false; errorMsg = "Could not generate key."; return@Button }
                    ref.child(key).updateChildren(mapOf("name" to name))
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
private fun WriterTextFieldDialog(catKey: String, writerKey: String, firebaseField: String, title: String, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
            .child("writers").child(writerKey).child(firebaseField)
            .get()
            .addOnSuccessListener { s ->
                text = s.getValue(String::class.java) ?: ""
                loaded = true
            }
            .addOnFailureListener { loaded = true }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(12.dp))
            if (!loaded) {
                CircularProgressIndicator(color = Color(0xFF12203D))
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 280.dp),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("Write here...") }
                )
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status, fontSize = 11.sp, color = Color(0xFF1F7A3D))
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        saving = true
                        FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
                            .child("writers").child(writerKey).child(firebaseField).setValue(text)
                            .addOnSuccessListener { saving = false; status = "Saved ✓" }
                            .addOnFailureListener { saving = false; status = "Failed to save" }
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(if (saving) "Saving..." else "Save", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

@Composable
private fun WorksListDialog(catKey: String, writerKey: String, writerName: String, onDismiss: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var works by remember { mutableStateOf<List<WorkEntry>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorWorkKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshTick) {
        loading = true
        FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
            .child("writers").child(writerKey).child("works")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                works = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    val t = child.child("title").getValue(String::class.java) ?: "Untitled"
                    val type = child.child("type").getValue(String::class.java) ?: "individual"
                    WorkEntry(key, t, type)
                }
            }
            .addOnFailureListener { loading = false }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text("$writerName — Works", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(12.dp))

            if (loading) {
                CircularProgressIndicator(color = Color(0xFF12203D))
            } else {
                if (works.isEmpty()) {
                    Text("No works added yet.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                    Spacer(Modifier.height(10.dp))
                } else {
                    val grouped = WORK_TYPE_SECTIONS.map { sec -> sec to works.filter { it.type == sec.key } }
                        .filter { it.second.isNotEmpty() }
                    Column(modifier = Modifier.heightIn(max = 380.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            grouped.forEach { (sec, secWorks) ->
                                item {
                                    Text(
                                        "${sec.label} (${secWorks.size})",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B6B79),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                items(secWorks) { w ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(w.title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
                                        TextButton(onClick = { editorWorkKey = w.key; editorOpen = true }) { Text("Edit") }
                                        TextButton(onClick = {
                                            FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
                                                .child("writers").child(writerKey).child("works").child(w.key).removeValue()
                                                .addOnSuccessListener { refreshTick++ }
                                        }) { Text("✕", color = Color(0xFFC0392B)) }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick = { editorWorkKey = null; editorOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("+ Add Work", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }

    if (editorOpen) {
        WorkEditorDialog(
            catKey = catKey,
            writerKey = writerKey,
            workKey = editorWorkKey,
            onDismiss = { editorOpen = false },
            onSaved = { editorOpen = false; refreshTick++ }
        )
    }
}

@Composable
private fun WorkEditorDialog(catKey: String, writerKey: String, workKey: String?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("individual") }
    var typeTouched by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    var characters by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf("") }
    var themes by remember { mutableStateOf("") }
    var questions by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(workKey == null) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(workKey) {
        if (workKey != null) {
            FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
                .child("writers").child(writerKey).child("works").child(workKey)
                .get()
                .addOnSuccessListener { s ->
                    title = s.child("title").getValue(String::class.java) ?: ""
                    type = s.child("type").getValue(String::class.java) ?: "individual"
                    typeTouched = true
                    summary = s.child("summary").getValue(String::class.java) ?: ""
                    characters = s.child("characters").getValue(String::class.java) ?: ""
                    lines = s.child("lines").getValue(String::class.java) ?: ""
                    themes = s.child("themes").getValue(String::class.java) ?: ""
                    questions = s.child("questions").getValue(String::class.java) ?: ""
                    loaded = true
                }
                .addOnFailureListener { loaded = true }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(if (workKey == null) "Add Work" else "Edit Work", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
            Spacer(Modifier.height(10.dp))

            if (!loaded) {
                CircularProgressIndicator(color = Color(0xFF12203D))
            } else {
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    WorkField("Title", title, singleLine = true) { newTitle ->
                        title = newTitle
                        if (!typeTouched) type = guessWorkType(newTitle)
                    }

                    Text("Category", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
                    Spacer(Modifier.height(6.dp))
                    WORK_TYPE_SECTIONS.chunked(3).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            rowTypes.forEach { sec ->
                                val active = type == sec.key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC), RoundedCornerShape(8.dp))
                                        .border(1.dp, if (active) Color(0xFF1B6B79) else Color(0xFFE3DFD3), RoundedCornerShape(8.dp))
                                        .clickable { type = sec.key; typeTouched = true }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(sec.label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            repeat(3 - rowTypes.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    WorkField("Summary", summary) { summary = it }
                    WorkField("Characters", characters) { characters = it }
                    WorkField("Important Lines", lines) { lines = it }
                    WorkField("Themes", themes) { themes = it }
                    WorkField("Questions (MCQ paste format)", questions) { questions = it }
                }

                if (errorMsg.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(errorMsg, fontSize = 11.sp, color = Color(0xFFC0392B))
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (title.isBlank()) { errorMsg = "Title is required."; return@Button }
                        saving = true
                        errorMsg = ""
                        val ref = FirebaseDatabase.getInstance().getReference("premiumContent").child(catKey)
                            .child("writers").child(writerKey).child("works")
                        val targetKey = workKey ?: ref.push().key
                        if (targetKey == null) { saving = false; errorMsg = "Could not generate key."; return@Button }
                        val updates = mapOf(
                            "title" to title, "type" to type, "summary" to summary, "characters" to characters,
                            "lines" to lines, "themes" to themes, "questions" to questions
                        )
                        ref.child(targetKey).updateChildren(updates)
                            .addOnSuccessListener { saving = false; onSaved() }
                            .addOnFailureListener { saving = false; errorMsg = "Failed to save." }
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(if (saving) "Saving..." else "Save Work", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun WorkField(label: String, value: String, singleLine: Boolean = false, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().let { if (!singleLine) it.heightIn(min = 90.dp) else it },
            shape = RoundedCornerShape(10.dp),
            singleLine = singleLine
        )
    }
}

package com.shreeyog.engteck.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AdminEntry(val key: String, val lines: List<String>)

data class AdminStudentEntry(
    val key: String = "",
    val name: String = "",
    val nickname: String = "",
    val category: String = "",       // "tgt" / "pgt" / "lt" / "gic"
    val isLive: Boolean = false,
    val amount: Int = 0,
    val mobile: String = "",
    val durationMonths: Int = 1,
    val registeredDate: String = "", // "yyyy-MM-dd"
    val validTill: String = "",      // human readable, e.g. "18 Sept 2026"
    val timestamp: Long = 0L
) {
    val displayName: String
        get() = if (nickname.isNotBlank()) "$name (Nickname: $nickname)" else name
    val planType: String
        get() = if (isLive) "Live Class" else "No Live Class"
}

private val ADMIN_CATS = listOf("tgt" to "TGT", "pgt" to "PGT", "lt" to "LT", "gic" to "GIC")

// Same business identity already printed on the website receipts.
private const val COACHING_REG_NO = "UDYAM-UK-09-0013602"
private const val GSTIN = "05JTTPS1814R1ZL"

private fun dateOnly(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
private fun humanDate(millis: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
private fun humanDateTime(millis: Long): String = SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
private fun addMonthsHuman(fromMillis: Long, months: Int): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = fromMillis
    cal.add(Calendar.MONTH, months)
    return humanDate(cal.timeInMillis)
}
private fun receiptNumber(mobile: String, millis: Long): String {
    val last4 = mobile.takeLast(4).ifBlank { "0000" }
    return "RCPT-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(millis))}-$last4"
}

private fun openWhatsApp(context: android.content.Context, mobile: String, message: String) {
    val digits = mobile.filter { it.isDigit() }
    val withCountryCode = if (digits.length == 10) "91$digits" else digits
    val url = "https://wa.me/$withCountryCode?text=${Uri.encode(message)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

private fun buildWelcomeMessage(s: AdminStudentEntry): String =
    "Hi ${s.name}, aapka Shree English Classes registration confirm ho gaya hai.\n\n" +
        "Class: ${s.category.uppercase()}\nPlan: ${s.planType} — ${s.durationMonths} month(s) — ₹${s.amount}\n" +
        "Valid till: ${s.validTill}\n\nDhanyavaad!"

@Composable
fun AdminDataViewersCard() {
    var activeTab by remember { mutableStateOf("registrations") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Data Viewer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("registrations" to "Registrations", "inquiries" to "Inquiries", "helpFeedback" to "Feedback").forEach { (key, label) ->
                val active = activeTab == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC))
                        .clickable { activeTab = key }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (activeTab == "registrations") {
            AdminRegistrationsCardView()
        } else {
            AdminEntryList(firebasePath = activeTab)
        }
    }
}

@Composable
private fun AdminRegistrationsCardView() {
    val context = LocalContext.current
    var activeCat by remember { mutableStateOf("tgt") }
    var loading by remember { mutableStateOf(true) }
    var allStudents by remember { mutableStateOf<List<AdminStudentEntry>>(emptyList()) }
    var reloadTrigger by remember { mutableStateOf(0) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<AdminStudentEntry?>(null) }
    var receiptStudent by remember { mutableStateOf<AdminStudentEntry?>(null) }
    var deletingStudent by remember { mutableStateOf<AdminStudentEntry?>(null) }

    LaunchedEffect(reloadTrigger) {
        loading = true
        FirebaseDatabase.getInstance().getReference("registrations")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                allStudents = snapshot.children.mapNotNull { r ->
                    val category = r.child("planCategory").getValue(String::class.java) ?: return@mapNotNull null
                    val realName = r.child("name").getValue(String::class.java) ?: "-"
                    val nickname = r.child("nickname").getValue(String::class.java) ?: ""
                    val planType = r.child("planType").getValue(String::class.java) ?: ""
                    val isLive = planType.startsWith("Live Class") && !planType.startsWith("No Live")
                    val amount = r.child("planAmount").getValue(Long::class.java)?.toInt() ?: 0
                    val mobile = r.child("mobile").getValue(String::class.java) ?: ""
                    val durationMonths = r.child("durationMonths").getValue(Long::class.java)?.toInt() ?: 1
                    val timestamp = r.child("timestamp").getValue(Long::class.java) ?: 0L
                    val registeredDate = r.child("registeredDate").getValue(String::class.java)
                        ?: if (timestamp > 0) dateOnly(timestamp) else ""
                    val validTill = r.child("validTill").getValue(String::class.java)
                        ?: if (timestamp > 0) addMonthsHuman(timestamp, durationMonths) else ""
                    AdminStudentEntry(
                        key = r.key ?: "", name = realName, nickname = nickname, category = category,
                        isLive = isLive, amount = amount, mobile = mobile, durationMonths = durationMonths,
                        registeredDate = registeredDate, validTill = validTill, timestamp = timestamp
                    )
                }.sortedByDescending { it.timestamp }
            }
            .addOnFailureListener { loading = false }
    }

    Button(
        onClick = { showAddDialog = true },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        Text("+ Add Student Manually", color = Color(0xFF12203D), fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ADMIN_CATS.forEach { (key, label) ->
            val active = activeCat == key
            val count = allStudents.count { it.category.equals(key, ignoreCase = true) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC))
                    .border(1.5.dp, if (active) Color(0xFF1B6B79) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                    .clickable { activeCat = key }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$label ($count)", color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    val filtered = allStudents.filter { it.category.equals(activeCat, ignoreCase = true) }

    if (loading) {
        CircularProgressIndicator(color = Color(0xFF12203D))
    } else if (filtered.isEmpty()) {
        Text("No registered students in this category.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
    } else {
        Column(modifier = Modifier.heightIn(max = 560.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(filtered, key = { it.key }) { s ->
                    val initial = s.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFF1B6B79), Color(0xFF12203D)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initial, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.displayName, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                                if (s.mobile.isNotEmpty()) {
                                    Text(s.mobile, fontSize = 10.5.sp, color = Color(0xFF5B5F6B))
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFE8EEF7), RoundedCornerShape(100.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(s.category.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B6EA8))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(if (s.isLive) Color(0xFFE3F5E9) else Color(0xFFF0EEE7), RoundedCornerShape(100.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (s.isLive) "Live" else "No Live",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (s.isLive) Color(0xFF1F7A3D) else Color(0xFF5B5F6B)
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFDF6E3), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text("₹${s.amount}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD4A017))
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1F7A3D))
                                    .clickable {
                                        openWhatsApp(context, s.mobile, buildWelcomeMessage(s))
                                    }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("WhatsApp", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF3B6EA8))
                                    .clickable { editingStudent = s }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("Edit", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFD4A017))
                                    .clickable { receiptStudent = s }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("🧾 Receipt", color = Color(0xFF12203D), fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFC0392B))
                                    .clickable { deletingStudent = s }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("Delete", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        StudentFormDialog(
            title = "Add Student Manually",
            existing = null,
            defaultCategory = activeCat,
            onDismiss = { showAddDialog = false },
            onSave = { form ->
                val now = System.currentTimeMillis()
                val ref = FirebaseDatabase.getInstance().getReference("registrations").push()
                val data = mapOf(
                    "name" to form.name,
                    "nickname" to form.nickname,
                    "mobile" to form.mobile,
                    "planCategory" to form.category,
                    "planType" to if (form.isLive) "Live Class" else "No Live Class",
                    "planAmount" to form.amount,
                    "durationMonths" to form.durationMonths,
                    "timestamp" to now,
                    "registeredDate" to dateOnly(now),
                    "validTill" to addMonthsHuman(now, form.durationMonths)
                )
                ref.setValue(data).addOnCompleteListener { reloadTrigger++ }
                showAddDialog = false
            }
        )
    }

    editingStudent?.let { s ->
        StudentFormDialog(
            title = "Edit Student",
            existing = s,
            defaultCategory = s.category,
            onDismiss = { editingStudent = null },
            onSave = { form ->
                val updates = mapOf(
                    "name" to form.name,
                    "nickname" to form.nickname,
                    "mobile" to form.mobile,
                    "planCategory" to form.category,
                    "planType" to if (form.isLive) "Live Class" else "No Live Class",
                    "planAmount" to form.amount,
                    "durationMonths" to form.durationMonths,
                    "validTill" to addMonthsHuman(
                        if (s.timestamp > 0) s.timestamp else System.currentTimeMillis(),
                        form.durationMonths
                    )
                )
                FirebaseDatabase.getInstance().getReference("registrations").child(s.key)
                    .updateChildren(updates).addOnCompleteListener { reloadTrigger++ }
                editingStudent = null
            }
        )
    }

    receiptStudent?.let { s ->
        ReceiptDialog(student = s, onDismiss = { receiptStudent = null })
    }

    deletingStudent?.let { s ->
        AlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = { Text("Delete ${s.name}?", fontWeight = FontWeight.Bold) },
            text = { Text("Ye registration hamesha ke liye delete ho jayegi. Confirm karein?") },
            confirmButton = {
                TextButton(onClick = {
                    FirebaseDatabase.getInstance().getReference("registrations").child(s.key)
                        .removeValue().addOnCompleteListener { reloadTrigger++ }
                    deletingStudent = null
                }) { Text("Delete", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingStudent = null }) { Text("Cancel") }
            }
        )
    }
}

private data class StudentFormData(
    val name: String, val nickname: String, val mobile: String, val category: String,
    val isLive: Boolean, val durationMonths: Int, val amount: Int
)

@Composable
private fun StudentFormDialog(
    title: String,
    existing: AdminStudentEntry?,
    defaultCategory: String,
    onDismiss: () -> Unit,
    onSave: (StudentFormData) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nickname by remember { mutableStateOf(existing?.nickname ?: "") }
    var mobile by remember { mutableStateOf(existing?.mobile ?: "") }
    var category by remember { mutableStateOf(existing?.category?.lowercase() ?: defaultCategory) }
    var isLive by remember { mutableStateOf(existing?.isLive ?: false) }
    var duration by remember { mutableStateOf((existing?.durationMonths ?: 1).toString()) }
    var amount by remember { mutableStateOf((existing?.amount ?: 799).toString()) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF12203D)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Student Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mobile, onValueChange = { if (it.length <= 10) mobile = it },
                    label = { Text("Mobile Number *") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ADMIN_CATS.forEach { (key, label) ->
                        val active = category == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) Color(0xFF1B6B79) else Color(0xFFF5F3EC))
                                .clickable { category = key }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(false to "No Live Class", true to "Live Class").forEach { (v, label) ->
                        val active = isLive == v
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (active) Color(0xFF1F7A3D) else Color(0xFFF5F3EC))
                                .clickable { isLive = v }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = if (active) Color.White else Color(0xFF5B5F6B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = duration, onValueChange = { duration = it.filter { c -> c.isDigit() } },
                        label = { Text("Months") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount ₹") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color(0xFFC0392B), fontSize = 11.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || mobile.length != 10) {
                    error = "Naam aur 10-digit mobile number zaroori hai"
                    return@TextButton
                }
                onSave(
                    StudentFormData(
                        name = name.trim(), nickname = nickname.trim(), mobile = mobile.trim(),
                        category = category, isLive = isLive,
                        durationMonths = duration.toIntOrNull() ?: 1,
                        amount = amount.toIntOrNull() ?: 0
                    )
                )
            }) { Text("Save", color = Color(0xFF1F7A3D), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReceiptDialog(student: AdminStudentEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val receiptNo = receiptNumber(student.mobile, if (student.timestamp > 0) student.timestamp else System.currentTimeMillis())
    val dateStr = student.registeredDate.ifBlank {
        dateOnly(if (student.timestamp > 0) student.timestamp else System.currentTimeMillis())
    }
    val receiptText = buildString {
        append("Shree English Classes\nPAYMENT RECEIPT\n\n")
        append("Receipt No: $receiptNo\nDate: $dateStr\n\n")
        append("Student Name: ${student.name}\n")
        append("Mobile: ${student.mobile}\n")
        append("Class: ${student.category.uppercase()}\n")
        append("Plan: ${student.planType} — ${student.durationMonths} month(s)\n")
        append("Valid Till: ${student.validTill}\n\n")
        append("Amount Paid: ₹${student.amount}\n\n")
        append("Thank you for registering!\n")
        append("Coaching Reg. No: $COACHING_REG_NO | GSTIN: $GSTIN")
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12203D))
                    .padding(vertical = 22.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Shree English Classes", color = Color(0xFFD4A017), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("PAYMENT RECEIPT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFFD4A017)))

            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Receipt No:", fontSize = 11.sp, color = Color(0xFF5B5F6B))
                        Text(receiptNo, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Date:", fontSize = 11.sp, color = Color(0xFF5B5F6B))
                        Text(dateStr, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFE3DFD3))
                Spacer(Modifier.height(14.dp))

                listOf(
                    "Student Name" to student.name,
                    "Mobile" to student.mobile,
                    "Class" to student.category.uppercase(),
                    "Plan" to "${student.planType} — ${student.durationMonths} month(s)",
                    "Valid Till" to student.validTill
                ).forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 12.5.sp, color = Color(0xFF5B5F6B))
                        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F3EC), RoundedCornerShape(12.dp))
                        .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Amount Paid", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                    Text("₹${student.amount}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF12203D))
                }

                Spacer(Modifier.height(14.dp))
                Text("Thank you for registering!", fontSize = 12.5.sp, color = Color(0xFF5B5F6B), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    "Coaching Reg. No: $COACHING_REG_NO | GSTIN: $GSTIN",
                    fontSize = 10.sp, color = Color(0xFF8A8F99), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, receiptText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Receipt"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017)),
                        modifier = Modifier.weight(1f)
                    ) { Text("Download", color = Color(0xFF12203D), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    Button(
                        onClick = { openWhatsApp(context, student.mobile, receiptText) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F7A3D)),
                        modifier = Modifier.weight(1f)
                    ) { Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun AdminEntryList(firebasePath: String) {
    var loading by remember(firebasePath) { mutableStateOf(true) }
    var entries by remember(firebasePath) { mutableStateOf<List<AdminEntry>>(emptyList()) }

    LaunchedEffect(firebasePath) {
        loading = true
        FirebaseDatabase.getInstance().getReference(firebasePath)
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                entries = snapshot.children.map { child ->
                    val lines = mutableListOf<String>()
                    child.children.forEach { field ->
                        if (field.key != "timestamp") {
                            val v = field.value?.toString() ?: ""
                            if (v.isNotBlank()) lines.add("${field.key}: $v")
                        }
                    }
                    AdminEntry(child.key ?: "", lines)
                }.reversed()
            }
            .addOnFailureListener { loading = false }
    }

    if (loading) {
        CircularProgressIndicator(color = Color(0xFF12203D))
    } else if (entries.isEmpty()) {
        Text("No entries yet.", fontSize = 12.sp, color = Color(0xFF5B5F6B))
    } else {
        LazyColumn(modifier = Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F3EC), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    entry.lines.forEach { line ->
                        Text(line, fontSize = 11.5.sp, color = Color(0xFF1A1A1A))
                    }
                }
            }
        }
    }
}

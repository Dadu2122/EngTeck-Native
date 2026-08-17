package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminAddRegistrationCard() {
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("tgt") }
    var categoryOpen by remember { mutableStateOf(false) }
    var isLive by remember { mutableStateOf(false) }
    var months by remember { mutableStateOf("1") }
    var amount by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val catOptions = listOf("tgt" to "TGT", "pgt" to "PGT", "lt" to "LT Grade", "gic" to "GIC Lecturer")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text("Add Registration Manually", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(14.dp))

        AdminSimpleField("Name", name) { name = it }
        AdminSimpleField("Nickname", nickname) { nickname = it }
        AdminSimpleField("Mobile Number", mobile, keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone) { if (it.length <= 10) mobile = it }

        Text("Class", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.padding(bottom = 12.dp)) {
            OutlinedButton(onClick = { categoryOpen = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Text(catOptions.first { it.first == category }.second, color = Color(0xFF1A1A1A))
            }
            DropdownMenu(expanded = categoryOpen, onDismissRequest = { categoryOpen = false }) {
                catOptions.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { category = key; categoryOpen = false })
                }
            }
        }

        Text("Nature", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            FilterChipLike("Live", isLive) { isLive = true }
            FilterChipLike("No Live", !isLive) { isLive = false }
        }

        AdminSimpleField("Duration (months)", months, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) { months = it }
        AdminSimpleField("Amount Paid (₹)", amount, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) { amount = it }

        Button(
            onClick = {
                if (name.isBlank() || mobile.length != 10) {
                    status = "Name and a 10-digit mobile number are required"
                    return@Button
                }
                saving = true
                status = ""
                val monthsInt = months.toIntOrNull() ?: 1
                val amountInt = amount.toIntOrNull() ?: 0
                val today = Calendar.getInstance()
                val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val feeDate = isoFormat.format(today.time)
                val expiryCal = today.clone() as Calendar
                expiryCal.add(Calendar.MONTH, monthsInt)
                val planExpiry = displayFormat.format(expiryCal.time)
                val planExpiryISO = isoFormat.format(expiryCal.time)

                val entry = mapOf(
                    "name" to name,
                    "nickname" to nickname,
                    "mobile" to mobile,
                    "state" to "",
                    "district" to "",
                    "email" to "",
                    "exam" to "",
                    "planCategory" to category,
                    "planType" to if (isLive) "Live Class" else "No Live Class",
                    "planDurationMonths" to monthsInt,
                    "planAmount" to amountInt,
                    "feeDate" to feeDate,
                    "planExpiry" to planExpiry,
                    "planExpiryISO" to planExpiryISO,
                    "submittedAt" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                    "addedByAdmin" to true
                )
                FirebaseDatabase.getInstance().getReference("registrations").push().setValue(entry)
                    .addOnSuccessListener {
                        saving = false
                        status = "Student added ✓"
                        name = ""; nickname = ""; mobile = ""; months = "1"; amount = ""
                    }
                    .addOnFailureListener {
                        saving = false
                        status = "Failed to add student"
                    }
            },
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(if (saving) "Adding..." else "Add Student", fontWeight = FontWeight.Bold)
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(status, fontSize = 12.sp, color = Color(0xFF1F7A3D))
        }
    }
}

@Composable
fun AdminSimpleField(
    label: String,
    value: String,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    onChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

@Composable
fun FilterChipLike(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) Color(0xFF1B6B79) else Color(0xFFF5F3EC), RoundedCornerShape(100.dp))
            .border(1.dp, if (selected) Color(0xFF1B6B79) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.White else Color(0xFF5B5F6B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

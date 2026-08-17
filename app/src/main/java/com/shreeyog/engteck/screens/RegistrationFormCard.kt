package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

@Composable
fun RegistrationFormCard() {
    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var exam by remember { mutableStateOf("") }
    var examDropdownOpen by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    val examOptions = listOf("TGT", "PGT", "LT", "GIC Lecturer", "UPPSC", "UPHESC")

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        Text(
            "JOIN NOW",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Color(0xFF1B6B79)
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF6FA3D8), Color(0xFF3B6EA8), Color(0xFF2A5487))
                    ),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, Color(0xFF2A5487), RoundedCornerShape(12.dp))
                .padding(vertical = 13.dp)
        ) {
            Text(
                "Registration Form",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .border(1.5.dp, Color(0xFFD4A017))
                .padding(18.dp)
        ) {
            RegField("Aspirant Name", true, name) { name = it }
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF9CA3AF), RoundedCornerShape(10.dp))
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    "⚠️ Show My Real Name Publicly",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            RegField("Nickname (Progress List पर यही दिखेगा)", true, nickname) { nickname = it }
            Spacer(Modifier.height(4.dp))
            Text(
                "आपका असली नाम कभी public नहीं दिखेगा। असली नाम सिर्फ Payment Receipt पर अंकित होगा। Progress Analytics और Leaderboard पर हमेशा यही Nickname दिखेगा।",
                fontSize = 11.sp,
                color = Color(0xFF5B5F6B),
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { RegField("State", true, state) { state = it } }
                Box(Modifier.weight(1f)) { RegField("District", true, district) { district = it } }
            }
            Spacer(Modifier.height(14.dp))

            RegField("Mobile", true, mobile, keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone) { mobile = it }
            Spacer(Modifier.height(14.dp))
            RegField("Email", false, email, keyboardType = androidx.compose.ui.text.input.KeyboardType.Email) { email = it }
            Spacer(Modifier.height(14.dp))

            Text("Select Exam *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
            Spacer(Modifier.height(6.dp))
            Box {
                OutlinedButton(
                    onClick = { examDropdownOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (exam.isEmpty()) "Choose one" else exam, color = Color(0xFF1A1A1A))
                }
                DropdownMenu(expanded = examDropdownOpen, onDismissRequest = { examDropdownOpen = false }) {
                    examOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            exam = option
                            examDropdownOpen = false
                        })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Payment-based registration will be added to the native app soon — for now registration is saved to Firebase, please complete payment via the web app.",
                fontSize = 11.sp,
                color = Color(0xFF946B00),
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    if (name.isBlank() || nickname.isBlank() || state.isBlank() || district.isBlank() || mobile.isBlank() || exam.isBlank()) {
                        statusMsg = "Please fill all required fields"
                        return@Button
                    }
                    submitting = true
                    statusMsg = ""
                    val entry = mapOf(
                        "name" to name, "nickname" to nickname, "state" to state, "district" to district,
                        "mobile" to mobile, "email" to email, "exam" to exam,
                        "paid" to false, "timestamp" to System.currentTimeMillis()
                    )
                    FirebaseDatabase.getInstance().getReference("registrations").push().setValue(entry)
                        .addOnSuccessListener {
                            submitting = false
                            statusMsg = "Registration submitted ✓"
                            name = ""; nickname = ""; state = ""; district = ""; mobile = ""; email = ""; exam = ""
                        }
                        .addOnFailureListener {
                            submitting = false
                            statusMsg = "Something went wrong, please try again"
                        }
                },
                enabled = !submitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D4C)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (submitting) "Submitting..." else "Register", fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (statusMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(statusMsg, fontSize = 12.sp, color = Color(0xFF1F7A3D))
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Already Registered? Login", color = Color(0xFF1A1A1A))
            }
        }
    }
}

@Composable
private fun RegField(
    label: String,
    required: Boolean,
    value: String,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    onChange: (String) -> Unit
) {
    Column {
        Text(
            if (required) "$label *" else label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1A1A)
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

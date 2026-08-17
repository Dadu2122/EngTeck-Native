package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase

@Composable
fun AdminLoginScreen(onLoginSuccess: (teacherKey: String, teacherName: String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔐", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text("Admin Login", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
        Spacer(Modifier.height(6.dp))
        Text("Enter your Admin PIN", fontSize = 13.sp, color = Color(0xFF5B5F6B))
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 10) pin = it },
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("••••") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
            )
        )

        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color(0xFFE85D4C), fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (pin.isBlank()) {
                    error = "PIN daalein"
                    return@Button
                }
                loading = true
                error = ""
                val db = FirebaseDatabase.getInstance()
                db.getReference("teachers")
                    .orderByChild("adminPin")
                    .equalTo(pin)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        loading = false
                        if (snapshot.exists()) {
                            val firstMatch = snapshot.children.first()
                            val teacherName = firstMatch.child("name").getValue(String::class.java) ?: "Teacher"
                            onLoginSuccess(firstMatch.key ?: "default", teacherName)
                        } else {
                            error = "गलत PIN।"
                        }
                    }
                    .addOnFailureListener {
                        loading = false
                        error = "Connection error — dobara try karein"
                    }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF12203D), strokeWidth = 2.dp)
            } else {
                Text("Login", fontWeight = FontWeight.Bold)
            }
        }
    }
}

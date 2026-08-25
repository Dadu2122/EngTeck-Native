package com.shreeyog.engteck.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.ui.theme.Gold
import com.shreeyog.engteck.ui.theme.InkSoft
import com.shreeyog.engteck.ui.theme.NavyDeep

@Composable
fun JoinCodeScreen(
    onValidCode: (joinCode: String, teacherKey: String, teacherName: String) -> Unit,
    prefillCode: String? = null
) {
    var code by remember { mutableStateOf(prefillCode?.uppercase() ?: "") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (code.isBlank()) {
            error = "Please enter a Join Code"
            return
        }
        loading = true
        error = null
        val db = FirebaseDatabase.getInstance()
        db.getReference("teachers")
            .orderByChild("joinCode")
            .equalTo(code)
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                if (snapshot.exists()) {
                    val firstMatch = snapshot.children.first()
                    val teacherKey = firstMatch.key ?: ""
                    val teacherName = firstMatch.child("name").getValue(String::class.java) ?: "Teacher"
                    onValidCode(code, teacherKey, teacherName)
                } else {
                    error = "This Join Code is not valid"
                }
            }
            .addOnFailureListener {
                loading = false
                error = "Connection error — please try again"
            }
    }

    // Coming from a tapped class-share link (code already known) — submit
    // right away instead of making the person retype what they already tapped.
    LaunchedEffect(prefillCode) {
        if (!prefillCode.isNullOrBlank()) submit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to EngTeck", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(6.dp))
        Text(
            "Enter your teacher's Join Code",
            fontSize = 14.sp,
            color = InkSoft
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase() },
            label = { Text("Join Code") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { submit() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = NavyDeep),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDeep, strokeWidth = 2.dp)
            } else {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.shreeyog.engteck.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.ui.theme.InkSoft
import com.shreeyog.engteck.ui.theme.NavyDeep

@Composable
fun SetDetailScreen(catKey: String, setKey: String, setTitle: String) {
    var loading by remember { mutableStateOf(true) }
    var questionsRaw by remember { mutableStateOf("") }

    LaunchedEffect(catKey, setKey) {
        FirebaseDatabase.getInstance().getReference("studySets")
            .child(catKey).child("sets").child(setKey).child("questionsRaw")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                questionsRaw = snapshot.getValue(String::class.java) ?: "Abhi content upload nahi hua hai."
            }
            .addOnFailureListener {
                loading = false
                questionsRaw = "Content load nahi ho paaya."
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(setTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyDeep)
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyDeep)
            }
        } else {
            Text(
                text = questionsRaw,
                fontSize = 14.sp,
                color = InkSoft,
                lineHeight = 22.sp
            )
        }
    }
}

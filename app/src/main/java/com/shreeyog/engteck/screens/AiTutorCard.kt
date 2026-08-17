package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

data class AiMessage(val text: String, val fromUser: Boolean)

@Composable
fun AiTutorCard() {
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<AiMessage>()) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)) {
        Text(
            "AI TUTOR",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Color(0xFF1B6B79)
        )
        Spacer(Modifier.height(6.dp))
        Text("🤖 Ask AI", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(4.dp))
        Text(
            "Ask questions, solve from a photo, generate a quiz, or find videos — anytime.",
            fontSize = 12.sp,
            color = Color(0xFF5B5F6B)
        )
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text("Hi! How can I help you?", fontSize = 13.sp, color = Color(0xFF5B5F6B), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickBtn("📷", "Scan to Solve", Modifier.weight(1f))
                QuickBtn("📝", "Get Quiz", Modifier.weight(1f))
                QuickBtn("▶️", "Watch Videos", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))

            if (messages.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages) { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (msg.fromUser) Color(0xFF1B6B79) else Color(0xFFF5F3EC),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    msg.text,
                                    color = if (msg.fromUser) Color.White else Color(0xFF1A1A1A),
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask your question…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(100.dp),
                    singleLine = true
                )
                IconButton(onClick = {
                    if (input.isNotBlank()) {
                        messages = messages + AiMessage(input, true) + AiMessage("The AI Tutor backend will be connected soon — this is a placeholder reply.", false)
                        input = ""
                    }
                }) {
                    Text("➤", fontSize = 18.sp, color = Color(0xFF1B6B79), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "AI can make mistakes — please verify important answers.",
                fontSize = 10.sp,
                color = Color(0xFF5B5F6B)
            )
        }
    }
}

@Composable
private fun QuickBtn(icon: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFFF5F3EC), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

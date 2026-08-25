package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SYLLABUS_ITEMS = listOf(
    "Writers and their personal details",
    "Details of all important works",
    "Work-wise important notes",
    "All Advanced Grammar rules/notes",
    "Literary theories",
    "Literary devices",
    "Figures of Speech",
    "Daily 50-Question Set (Grammar)",
    "Daily 50-Question Set (Term/Device/Theory/Age/Movement)",
    "Daily 50-Question Set (Literature)",
    "125-Question Set every Tuesday and Friday (All Topics)"
)

private data class SyllabusPlan(val name: String, val amount: String, val borderColor: Color, val textColor: Color)

private val SYLLABUS_PLANS = listOf(
    SyllabusPlan("TGT", "₹799/mo", Color(0xFF8FBBFA), Color(0xFF8FBBFA)),
    SyllabusPlan("PGT", "₹1099/mo", Color(0xFF3ECF8E), Color(0xFF7FE0B4)),
    SyllabusPlan("LT", "₹1099/mo", Color(0xFFF2A25C), Color(0xFFF5BE8A)),
    SyllabusPlan("GIC Lecturer", "₹1099/mo", Color(0xFFB48AF7), Color(0xFFCCAEFA))
)

@Composable
fun SyllabusPdfCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF12203D), Color(0xFF0B1730))))
            .border(1.5.dp, Color(0xFFD4A017))
            .padding(horizontal = 20.dp, vertical = 26.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE85D4C), RoundedCornerShape(12.dp))
                .padding(vertical = 13.dp, horizontal = 10.dp)
        ) {
            Text(
                "Complete Syllabus Through PDFs",
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))

        // Fixed height + capped line count here (instead of letting each card
        // size itself to its own text) stops the whole row — and everything
        // below it — from jumping up/down as you swipe: short items like
        // "Literary devices" and long ones like "125 Question-set every
        // Tuesday and Friday (All topics)" used to resize the row on every
        // scroll frame, which read as the cards "vibrating".
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SYLLABUS_ITEMS.withIndex().toList()) { (index, item) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .width(158.dp)
                        .height(64.dp)
                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(18.dp).background(Color(0xFFD4A017), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF12203D))
                    }
                    Text(
                        item,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Swipe to explore →",
            color = Color(0xFFF0D384),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )

        Spacer(Modifier.height(16.dp))
        SYLLABUS_PLANS.forEach { plan ->
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(
                        androidx.compose.foundation.BorderStroke(3.dp, plan.borderColor),
                        RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(plan.name.uppercase(), color = plan.textColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Text(plan.amount, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(6.dp))
        BlinkingText(
            "✨ Every Saturday and Sunday Live Test ✨",
            color = Color(0xFFD4A017),
            fontSize = 12.sp
        )
    }
}

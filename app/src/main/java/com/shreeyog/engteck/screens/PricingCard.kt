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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OFFER_ITEMS = listOf(
    "Live Classes", "Complete Syllabus", "Video Updates", "PDF Updates", "MCQs",
    "Practice Sets", "Literary Theories & Movements", "Literary Devices",
    "Advance Grammar", "Old Papers (Year-wise)", "TGT/PGT/LT/GIC Specifics", "Self Assessment"
)

private data class PricePlan(val name: String, val amount: String)

private val PRICE_PLANS = listOf(
    PricePlan("TGT (3 months)", "₹4999"),
    PricePlan("PGT (5 months)", "₹6999"),
    PricePlan("LT (5 months)", "₹6999"),
    PricePlan("GIC Lecturer (5 months)", "₹6999")
)

@Composable
fun PricingCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF12203D), Color(0xFF0B1730))
                )
            )
            .border(1.5.dp, Color(0xFFD4A017))
            .padding(horizontal = 20.dp, vertical = 26.dp)
    ) {
        Text(
            "After Registration You Will Get",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Plus daily updated content, every single day:",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.5.sp
        )
        Spacer(Modifier.height(18.dp))

        // Fixed height here (instead of letting each card size itself) stops
        // the whole row — and everything below it — from jumping up/down as
        // you swipe: 1-line items ("Live Classes") and 2-line items
        // ("Work-wise important notes") used to resize the row on every
        // scroll frame, which read as the cards "vibrating".
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(OFFER_ITEMS.withIndex().toList()) { (index, item) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .width(132.dp)
                        .height(52.dp)
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
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 13.sp,
                        maxLines = 2,
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
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFD4A017).copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFD4A017), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text("📅", fontSize = 14.sp)
            Column {
                Text(
                    "Duration:",
                    color = Color(0xFFF0D384),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "TGT — 3 Months\nPGT, LT & GIC Lecturer — 5 Months",
                    color = Color(0xFFF0D384),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "SUBSCRIPTION (LIVE CLASSES)",
            color = Color(0xFFF0D384),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(10.dp))

        PRICE_PLANS.forEach { plan ->
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(1.5.dp, Color(0xFFE0304A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    plan.name.uppercase(),
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    plan.amount,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "✨ Discount Applicable ✨",
            color = Color(0xFFD4A017),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

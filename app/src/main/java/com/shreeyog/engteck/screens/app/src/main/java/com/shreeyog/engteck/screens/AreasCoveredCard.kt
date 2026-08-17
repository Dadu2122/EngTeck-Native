package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AREAS = listOf(
    "English Grammar", "Literature", "History of English Literature", "Literary Movements",
    "Literary Theories", "Literary Devices/Terms", "Figures of Speech", "Syllabus-based MCQs",
    "Previous Papers", "Mock Tests", "Linguistics Special", "Literary Criticism", "Literary Forms"
)

@Composable
fun AreasCoveredCard() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        Text(
            "COVERAGE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Color(0xFF1B6B79)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Areas Covered",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.5.dp, Color(0xFFE3DFD3), RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(340.dp)
            ) {
                items(AREAS) { area ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE7F1F2), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            area,
                            color = Color(0xFF1B6B79),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

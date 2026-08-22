package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A tap-to-open / tap-to-close section for the Admin Panel. Collapsed by default so the panel
// doesn't turn into one giant endless scroll — only one card's content needs to be rendered
// (and hit Firebase) at a time when the admin actually wants to work on it.
//
// fullBleedContent: when true, the outer rounded-corner clip is skipped so this section's
// content (e.g. the live board) can extend edge-to-edge past the Admin Panel's side padding
// instead of being cropped by this card's own rounded corners. The header/card chrome loses
// its rounding in that case — an acceptable tradeoff since the bleeding content is the point.
@Composable
fun AdminAccordionSection(title: String, icon: String = "", fullBleedContent: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fullBleedContent) Modifier else Modifier.clip(RoundedCornerShape(16.dp)))
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE3DFD3), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (icon.isNotEmpty()) "$icon $title" else title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF12203D)
            )
            Text(
                if (expanded) "▲ Close" else "▼ Open",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B6B79)
            )
        }
        if (expanded) {
            Box(modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)) {
                content()
            }
        }
    }
}

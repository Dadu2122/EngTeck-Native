package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.ui.theme.InkSoft
import com.shreeyog.engteck.ui.theme.NavyDeep
import java.text.SimpleDateFormat
import java.util.*

data class MiniBook(
    val key: String,
    val title: String,
    val addedAt: Long,
    val downloads: Long
)

private val MINIBOOK_PALETTES = listOf(
    listOf(Color(0xFF1B6B79), Color(0xFF12203D)),
    listOf(Color(0xFFD4A017), Color(0xFF7A2E3D)),
    listOf(Color(0xFF3B6EA8), Color(0xFF12203D)),
    listOf(Color(0xFF7A2E3D), Color(0xFF2B0F16)),
    listOf(Color(0xFF1F7A3D), Color(0xFF0B1730)),
    listOf(Color(0xFFE85D4C), Color(0xFF7A2E3D)),
    listOf(Color(0xFF946B00), Color(0xFF12203D)),
    listOf(Color(0xFF22909F), Color(0xFF0B1730))
)

private fun minibookGradient(str: String): Brush {
    var hash = 0
    for (c in str) { hash = (hash * 31 + c.code) }
    val idx = ((hash % MINIBOOK_PALETTES.size) + MINIBOOK_PALETTES.size) % MINIBOOK_PALETTES.size
    val colors = MINIBOOK_PALETTES[idx]
    return Brush.linearGradient(colors)
}

@Composable
fun MiniBooksScreen(onBookClick: (key: String, title: String) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var books by remember { mutableStateOf<List<MiniBook>>(emptyList()) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("miniBooks")
            .get()
            .addOnSuccessListener { snapshot ->
                loading = false
                val list = snapshot.children.mapNotNull { child ->
                    val title = child.child("title").getValue(String::class.java) ?: return@mapNotNull null
                    val addedAt = child.child("addedAt").getValue(Long::class.java) ?: 0L
                    val downloads = child.child("downloads").getValue(Long::class.java) ?: 0L
                    MiniBook(child.key ?: "", title, addedAt, downloads)
                }.sortedByDescending { it.addedAt }
                books = list
            }
            .addOnFailureListener { loading = false }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Mini Books", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NavyDeep, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NavyDeep)
            }
        } else if (books.isEmpty()) {
            Text(
                "No books uploaded yet.",
                color = InkSoft,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                items(books) { book ->
                    MiniBookCard(book, onClick = { onBookClick(book.key, book.title) })
                }
            }
        }
    }
}

@Composable
private fun MiniBookCard(book: MiniBook, onClick: () -> Unit) {
    val dateLabel = if (book.addedAt > 0) {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(book.addedAt))
    } else ""

    Box(
        modifier = Modifier
            .width(150.dp)
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 10.dp))
            .background(minibookGradient(book.title))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(Color.Black.copy(alpha = 0.22f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("📖", fontSize = 20.sp)
            Column {
                Text(
                    book.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text("READ →", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                if (dateLabel.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("📅 $dateLabel", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                }
                Text("⬇ Total Downloads - ${book.downloads}", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }
    }
}

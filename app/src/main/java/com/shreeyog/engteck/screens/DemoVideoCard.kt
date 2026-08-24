package com.shreeyog.engteck.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay

/**
 * Extracts an 11-character YouTube video ID from common URL formats:
 * youtu.be/ID, youtube.com/watch?v=ID, youtube.com/embed/ID, youtube.com/shorts/ID
 */
private fun extractYouTubeId(url: String): String? {
    val patterns = listOf(
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
        Regex("v=([A-Za-z0-9_-]{11})"),
        Regex("embed/([A-Za-z0-9_-]{11})"),
        Regex("shorts/([A-Za-z0-9_-]{11})")
    )
    for (pattern in patterns) {
        pattern.find(url)?.let { return it.groupValues[1] }
    }
    // If it's already just a bare 11-char ID
    if (Regex("^[A-Za-z0-9_-]{11}$").matches(url.trim())) return url.trim()
    return null
}

@Composable
fun DemoVideoCard() {
    var videoUrl by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("content").child("demoVideoUrl")
            .get()
            .addOnSuccessListener { snapshot ->
                videoUrl = snapshot.getValue(String::class.java) ?: ""
            }
    }

    val videoId = remember(videoUrl) { extractYouTubeId(videoUrl) }

    var tapCount by remember { mutableIntStateOf(0) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf("") }

    // Reset tap counter if user pauses more than 1.5s between taps
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(1500)
            tapCount = 0
        }
    }

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Set Demo Video Link") },
            text = {
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    label = { Text("YouTube URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = linkInput.trim()
                    if (trimmed.isNotBlank()) {
                        FirebaseDatabase.getInstance().getReference("content")
                            .child("demoVideoUrl")
                            .setValue(trimmed)
                        videoUrl = trimmed
                    }
                    showLinkDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        Text(
            "WATCH NOW",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = Color(0xFF1B6B79)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Coaching and App Updates",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    tapCount++
                    if (tapCount >= 4) {
                        tapCount = 0
                        linkInput = videoUrl
                        showLinkDialog = true
                    }
                }
        )
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(14.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying && videoId != null) {
                    YouTubeEmbedPlayer(videoId = videoId)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(enabled = videoId != null) { isPlaying = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF0000).copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color.White, fontSize = 20.sp)
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12203D))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFF0000)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 8.sp)
                }
                Text("Class Demo — Watch Free", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbedPlayer(videoId: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webChromeClient = WebChromeClient()
                setBackgroundColor(0xFF000000.toInt())

                val html = """
                    <html>
                    <body style="margin:0;padding:0;background:#000;">
                    <iframe width="100%" height="100%"
                        src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
                        frameborder="0"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                        allowfullscreen>
                    </iframe>
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
    )
}

package com.shreeyog.engteck.screens

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.live.AgoraLiveAudio
import com.shreeyog.engteck.live.PdfSlideRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AdminLiveClassCard() {
    val context = LocalContext.current
    var isLive by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    var slidePdf by remember { mutableStateOf("") }
    var currentPage by remember { mutableStateOf(0) }
    var pageCount by remember { mutableStateOf(0) }
    var slideBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var repeatCount by remember { mutableStateOf(0) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploading = true
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val b64 = "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    FirebaseDatabase.getInstance().getReference("liveClasses/default/slidePdf").setValue(b64)
                    FirebaseDatabase.getInstance().getReference("liveClasses/default/currentPage").setValue(0)
                    msg = "Slides shared with the class."
                } else {
                    msg = "Could not read that file."
                }
            } catch (e: Exception) {
                msg = "Upload failed: ${e.message}"
            }
            uploading = false
        }
    }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("liveClasses/default/active")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    isLive = snapshot.getValue(Boolean::class.java) ?: false
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/default/slidePdf")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    slidePdf = snapshot.getValue(String::class.java) ?: ""
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/default/currentPage")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    currentPage = snapshot.getValue(Int::class.java) ?: 0
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        FirebaseDatabase.getInstance().getReference("liveClasses/default/repeatRequests")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    repeatCount = snapshot.childrenCount.toInt()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    LaunchedEffect(slidePdf, currentPage) {
        if (slidePdf.isNotBlank()) {
            val result = withContext(Dispatchers.Default) {
                val count = PdfSlideRenderer.pageCount(context, slidePdf)
                val bmp = PdfSlideRenderer.renderPage(context, slidePdf, currentPage)
                Pair(count, bmp)
            }
            pageCount = result.first
            slideBitmap = result.second
        } else {
            slideBitmap = null
            pageCount = 0
        }
    }

    fun startClass() {
        starting = true
        msg = "Starting class..."
        AgoraLiveAudio.onJoined = {
            starting = false
            connected = true
            msg = "You're live — students can join now."
            FirebaseDatabase.getInstance().getReference("liveClasses/default/active").setValue(true)
        }
        AgoraLiveAudio.onError = { err ->
            starting = false
            msg = "Could not start: $err"
        }
        AgoraLiveAudio.join(context, "default", 1)
    }

    fun stopClass() {
        AgoraLiveAudio.leave()
        connected = false
        msg = ""
        FirebaseDatabase.getInstance().getReference("liveClasses/default/active").setValue(false)
        FirebaseDatabase.getInstance().getReference("liveClasses/default/repeatRequests").removeValue()
    }

    fun goToPage(page: Int) {
        val clamped = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        FirebaseDatabase.getInstance().getReference("liveClasses/default/currentPage").setValue(clamped)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Box(
            modifier = Modifier
                .background(if (isLive) Color(0xFFE3F5E9) else Color(0xFFE3DFD3), RoundedCornerShape(100.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                if (isLive) "● Live Now" else "● Not Live",
                color = if (isLive) Color(0xFF1F7A3D) else Color(0xFF8A8F99),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))

        if (!connected) {
            Text(
                "Start the live class — students will be able to join and hear you as soon as you start.",
                fontSize = 12.5.sp,
                color = Color(0xFF5B5F6B)
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { startClass() },
                enabled = !starting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F7A3D)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (starting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("▶ Start Live Class", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        } else {
            Text("🔊 You are broadcasting", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F7A3D))
            Spacer(Modifier.height(14.dp))

            if (repeatCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFCF3D9), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("🔁 $repeatCount student(s) asked you to repeat", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF946B00))
                }
                Spacer(Modifier.height(10.dp))
            }

            if (slideBitmap != null) {
                Image(
                    bitmap = slideBitmap!!.asImageBitmap(),
                    contentDescription = "Slide preview",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE3DFD3))
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { goToPage(currentPage - 1) },
                        enabled = currentPage > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B79)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("‹ Prev", color = Color.White, fontWeight = FontWeight.Bold) }
                    Text("Page ${currentPage + 1} / $pageCount", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    Button(
                        onClick = { goToPage(currentPage + 1) },
                        enabled = currentPage < pageCount - 1,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B6B79)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Next ›", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(14.dp))
            }

            Button(
                onClick = { pdfPickerLauncher.launch("application/pdf") },
                enabled = !uploading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                if (uploading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (slidePdf.isBlank()) "📂 Share Slides (PDF)" else "📂 Change Slides", color = Color(0xFF12203D), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        muted = !muted
                        AgoraLiveAudio.setMuted(muted)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (muted) Color(0xFF8A8F99) else Color(0xFF1B6B79)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (muted) "🔇 Unmute" else "🎤 Mute", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { stopClass() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("⏹ Stop Class", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(msg, fontSize = 12.sp, color = Color(0xFF946B00))
        }
    }
}

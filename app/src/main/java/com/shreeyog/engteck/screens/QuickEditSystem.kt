package com.shreeyog.engteck.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.database.FirebaseDatabase

// Global, app-wide edit-mode flag. Any screen can read EditModeState.enabled;
// no prop-drilling needed since this is a simple observable singleton.
object EditModeState {
    var enabled by mutableStateOf(false)
}

// The 🎓 icon becomes this — 2 taps (within ~900ms of each other) toggles
// quick-edit mode on/off for the whole Home page.
@Composable
fun QuickEditToggleCap() {
    var tapCount by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .size(54.dp)
            .then(Modifier)
            .background(
                Brush.linearGradient(listOf(Color(0xFFD4A017), Color(0xFFB8860F))),
                RoundedCornerShape(16.dp)
            )
            .then(
                if (EditModeState.enabled)
                    Modifier.border(2.dp, Color(0xFF39FF9E), RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable {
                val now = System.currentTimeMillis()
                tapCount = if (now - lastTap > 900) 1 else tapCount + 1
                lastTap = now
                if (tapCount >= 2) {
                    tapCount = 0
                    EditModeState.enabled = !EditModeState.enabled
                }
            },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text("🎓", fontSize = 22.sp)
    }
}

/**
 * Wrap any piece of static text on the Home page with this to make it
 * quick-editable. While quick-edit mode is on (via the 🎓 icon), the text
 * gets a subtle blue outline; 3 taps on it opens a small edit box that
 * saves straight to Firebase content/{fieldKey}.
 *
 * Usage:
 *   var content by remember { mutableStateOf(SomeContent()) }
 *   EditableText("tagline", content.tagline, { content = content.copy(tagline = it) }) { v ->
 *       Text(v, fontSize = 14.sp, color = Color.White)
 *   }
 */
@Composable
fun EditableText(
    fieldKey: String,
    value: String,
    onSaved: (String) -> Unit,
    content: @Composable (String) -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }
    var showEditDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }
    var saving by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .then(
                if (EditModeState.enabled)
                    Modifier.border(1.dp, Color(0xFF3B6EA8), RoundedCornerShape(4.dp))
                else Modifier
            )
            .then(
                if (EditModeState.enabled) {
                    Modifier.clickable {
                        val now = System.currentTimeMillis()
                        tapCount = if (now - lastTap > 900) 1 else tapCount + 1
                        lastTap = now
                        if (tapCount >= 3) {
                            tapCount = 0
                            draft = value
                            showEditDialog = true
                        }
                    }
                } else Modifier
            )
    ) {
        content(value)
    }

    if (showEditDialog) {
        Dialog(onDismissRequest = { showEditDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Text("Edit Text", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF12203D))
                Spacer(Modifier.height(4.dp))
                Text(fieldKey, fontSize = 10.5.sp, color = Color(0xFF8A8F99))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 1,
                    maxLines = 6
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showEditDialog = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            saving = true
                            FirebaseDatabase.getInstance().getReference("content").child(fieldKey)
                                .setValue(draft)
                                .addOnSuccessListener {
                                    saving = false
                                    onSaved(draft)
                                    showEditDialog = false
                                }
                                .addOnFailureListener { saving = false }
                        },
                        enabled = !saving,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4A017), contentColor = Color(0xFF12203D)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (saving) "Saving..." else "Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

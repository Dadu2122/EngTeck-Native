package com.shreeyog.engteck

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreeyog.engteck.navigation.EngTeckNavGraph
import com.shreeyog.engteck.ui.theme.EngTeckTheme
import java.io.PrintWriter
import java.io.StringWriter

private const val CRASH_PREFS = "engteck_crash_log"
private const val CRASH_KEY = "last_crash"

// Any uncaught crash anywhere in the app gets its full stack trace saved
// here, then the app is allowed to close as normal (so the phone still
// shows its usual "keeps stopping" dialog). The NEXT time the app opens,
// onCreate below finds this saved trace and shows it in a dialog — this is
// how we read crash details without ADB/Logcat on a phone-only setup.
private fun installCrashCatcher(context: Context) {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(CRASH_KEY, sw.toString())
                .apply()
        } catch (e: Exception) {
            // If even saving the crash fails, there's nothing more we can do here.
        }
        previousHandler?.uncaughtException(thread, throwable)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashCatcher(applicationContext)

        val prefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
        val savedCrash = prefs.getString(CRASH_KEY, null)

        setContent {
            EngTeckTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EngTeckNavGraph()
                }

                var crashText by remember { mutableStateOf(savedCrash) }
                if (crashText != null) {
                    AlertDialog(
                        onDismissRequest = { /* force them to use the button — don't lose this by an accidental tap outside */ },
                        title = { Text("Last crash") },
                        text = {
                            Text(
                                crashText ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                prefs.edit().remove(CRASH_KEY).apply()
                                crashText = null
                            }) { Text("Got it, clear") }
                        }
                    )
                }
            }
        }
    }
}

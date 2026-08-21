package com.shreeyog.engteck.live

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.io.FileOutputStream

/**
 * Loads the live-class "Smart Digital Board" straight from the website
 * (dadu2122.github.io/Shree-English-Classes) instead of re-building it
 * natively — so it looks and behaves pixel-for-pixel identical to index.html:
 * same full-bleed board, same annotation tools, same PDF rendering, same
 * Firebase sync. This removes the native layout bugs entirely, since the
 * board itself is literally the website's own code running in a WebView.
 *
 * Native Agora audio (AgoraLiveAudio) stays exactly as it is — this only
 * replaces the visual board/annotation/PDF portion.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveClassBoardWebView(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Always fetch the latest board — never serve a stale cached
                // version, matching the WebView app's existing behaviour.
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                // Mirrors the "AndroidApp" bridge already used in
                // Shree-English-Classes-WebView-App/MainActivity.kt, so the
                // site's existing saveBase64File() calls (snapshot / PDF
                // download) work exactly the same way inside this native app.
                addJavascriptInterface(AndroidBoardBridge(context), "AndroidApp")
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) webView.loadUrl(url)
        }
    )
}

private class AndroidBoardBridge(private val context: Context) {
    @JavascriptInterface
    fun saveBase64File(base64Data: String, fileName: String) {
        try {
            val clean = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

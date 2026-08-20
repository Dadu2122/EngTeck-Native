package com.shreeyog.engteck.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

// Renders one page of a base64-encoded PDF to a Bitmap using Android's built-in PdfRenderer
// (no external library needed — keeps APK size down). Caches the currently-loaded PDF so
// switching pages doesn't re-decode the whole file every time, only reloads when the PDF itself
// changes (teacher shares a new deck).
object PdfSlideRenderer {
    private var pdfFile: File? = null
    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private var loadedHash: Int? = null

    fun pageCount(context: Context, base64Pdf: String): Int {
        val r = ensureLoaded(context, base64Pdf) ?: return 0
        return r.pageCount
    }

    fun renderPage(context: Context, base64Pdf: String, pageIndex: Int): Bitmap? {
        val r = ensureLoaded(context, base64Pdf) ?: return null
        if (pageIndex < 0 || pageIndex >= r.pageCount) return null
        return try {
            val page = r.openPage(pageIndex)
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun ensureLoaded(context: Context, base64Pdf: String): PdfRenderer? {
        val hash = base64Pdf.hashCode()
        if (loadedHash == hash && renderer != null) return renderer
        close()
        return try {
            val raw = if (base64Pdf.contains(",")) base64Pdf.substringAfter(",") else base64Pdf
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            val file = File(context.cacheDir, "live_slide_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { it.write(bytes) }
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(descriptor)
            pdfFile = file
            fd = descriptor
            renderer = r
            loadedHash = hash
            r
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        try { renderer?.close() } catch (e: Exception) {}
        try { fd?.close() } catch (e: Exception) {}
        try { pdfFile?.delete() } catch (e: Exception) {}
        renderer = null
        fd = null
        pdfFile = null
        loadedHash = null
    }
}

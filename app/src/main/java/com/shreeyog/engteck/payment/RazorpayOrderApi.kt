package com.shreeyog.engteck.payment

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Same Vercel serverless endpoint the web app calls (create-razorpay-order.js) — it creates the
// Razorpay order server-side and returns the orderId/amount/keyId needed to open Checkout. The
// matching webhook (razorpay-webhook.js), already deployed on the same project, is what actually
// writes the payment confirmation to Firebase after Razorpay verifies the payment — so this app
// never needs to know the Razorpay secret key, exactly like the web app.
private const val RAZORPAY_ORDER_SERVER = "https://shreeyog-agora-token-server.vercel.app/api/create-razorpay-order"

data class RazorpayOrderResult(val orderId: String, val amount: Long, val keyId: String)

suspend fun createRazorpayOrder(amount: Int, bookKey: String, mobile: String, title: String): RazorpayOrderResult? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(RAZORPAY_ORDER_SERVER)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val body = JSONObject().apply {
                put("amount", amount)
                put("bookKey", bookKey)
                put("mobile", mobile)
                put("title", title)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseText)
            if (!json.has("orderId")) return@withContext null
            RazorpayOrderResult(
                orderId = json.getString("orderId"),
                amount = json.getLong("amount"),
                keyId = json.getString("keyId")
            )
        } catch (e: Exception) {
            null
        }
    }
}

// Builds the Intent to launch the WebView-based checkout screen (RazorpayWebCheckoutActivity).
// Launch this via rememberLauncherForActivityResult in the Composable and read the "paymentId"
// extra from the result on success.
fun buildRazorpayCheckoutIntent(
    context: Context,
    order: RazorpayOrderResult,
    mobile: String,
    description: String,
    coachingName: String
): Intent {
    return Intent(context, RazorpayWebCheckoutActivity::class.java).apply {
        putExtra("keyId", order.keyId)
        putExtra("orderId", order.orderId)
        putExtra("amount", order.amount)
        putExtra("name", coachingName)
        putExtra("description", description)
        putExtra("contact", mobile)
    }
}

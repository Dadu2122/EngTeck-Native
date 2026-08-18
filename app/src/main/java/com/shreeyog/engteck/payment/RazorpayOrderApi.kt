package com.shreeyog.engteck.payment

import android.app.Activity
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

// Opens Razorpay Checkout on the given Activity (must be MainActivity, which implements
// PaymentResultListener). Register success/error callbacks via RazorpayBridge before calling
// this, since Checkout's result comes back through the Activity, not directly here.
fun openRazorpayCheckout(
    activity: Activity,
    order: RazorpayOrderResult,
    mobile: String,
    description: String,
    coachingName: String
) {
    val checkout = com.razorpay.Checkout()
    checkout.setKeyID(order.keyId)
    val options = JSONObject().apply {
        put("name", coachingName)
        put("description", description)
        put("order_id", order.orderId)
        put("currency", "INR")
        put("amount", order.amount)
        put("theme.color", "#12203D")
        put("prefill.contact", mobile)
    }
    try {
        checkout.open(activity, options)
    } catch (e: Exception) {
        RazorpayBridge.notifyError(-1, "Could not open payment window: ${e.message}")
    }
}


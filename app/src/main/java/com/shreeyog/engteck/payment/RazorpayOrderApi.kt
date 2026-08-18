package com.shreeyog.engteck.payment

import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
        val upiBlock = JSONObject().apply {
            put("name", "Pay using UPI")
            put("instruments", org.json.JSONArray().apply {
                put(JSONObject().apply { put("method", "upi") })
            })
        }
        val cardBlock = JSONObject().apply {
            put("name", "Pay using Card")
            put("instruments", org.json.JSONArray().apply {
                put(JSONObject().apply { put("method", "card") })
            })
        }
        val blocks = JSONObject().apply {
            put("upi", upiBlock)
            put("card", cardBlock)
        }
        val display = JSONObject().apply {
            put("blocks", blocks)
            put("sequence", org.json.JSONArray().apply {
                put("block.upi")
                put("block.card")
            })
            put("preferences", JSONObject().apply {
                put("show_default_blocks", true)
            })
        }
        put("config", JSONObject().apply {
            put("display", display)
        })
    }
    try {
        checkout.open(activity, options)
    } catch (e: Exception) {
        RazorpayBridge.notifyError(-1, "Could not open payment window: ${e.message}")
    }
}

package com.shreeyog.engteck.payment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

// Loads Razorpay's own hosted checkout.js inside a WebView — exactly the same mechanism the
// old WebView-based APK already used successfully (UPI, Cards, Netbanking, Wallet all show up
// automatically because this is the real Razorpay web checkout page, not the native Android SDK
// which has known UPI-detection bugs on newer Android versions). Results come back via a small
// JS bridge that sets the Activity result and finishes.
class RazorpayWebCheckoutActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyId = intent.getStringExtra("keyId") ?: ""
        val orderId = intent.getStringExtra("orderId") ?: ""
        val amount = intent.getLongExtra("amount", 0L)
        val name = intent.getStringExtra("name") ?: ""
        val description = intent.getStringExtra("description") ?: ""
        val contact = intent.getStringExtra("contact") ?: ""

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // Any non-http(s) link here is a UPI app trying to open (upi://, tez://,
                // phonepe://, paytmmp://, credpay://, etc.) — a plain WebView has no idea what
                // to do with these, so without this override Razorpay just hides the whole UPI
                // section since it knows tapping it would silently fail.
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        true
                    } catch (e: ActivityNotFoundException) {
                        true
                    }
                }
                return false
            }
        }
        webView.addJavascriptInterface(JsBridge(), "AndroidPay")
        setContentView(webView)

        val html = """
            <!DOCTYPE html>
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
            </head><body style="margin:0;padding:0;background:#12203D;">
            <script>
              var options = {
                "key": "$keyId",
                "amount": "$amount",
                "currency": "INR",
                "name": "$name",
                "description": "$description",
                "order_id": "$orderId",
                "prefill": { "contact": "$contact" },
                "theme": { "color": "#12203D" },
                "handler": function (response){
                  AndroidPay.onSuccess(response.razorpay_payment_id);
                },
                "modal": {
                  "ondismiss": function(){
                    AndroidPay.onDismiss();
                  }
                }
              };
              var rzp = new Razorpay(options);
              rzp.on('payment.failed', function (response){
                AndroidPay.onFailure(response.error.description || 'Payment failed');
              });
              rzp.open();
            </script>
            </body></html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://checkout.razorpay.com", html, "text/html", "utf-8", null)
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onSuccess(paymentId: String) {
            runOnUiThread {
                val data = Intent().putExtra("paymentId", paymentId)
                setResult(RESULT_OK, data)
                finish()
            }
        }

        @JavascriptInterface
        fun onFailure(message: String) {
            runOnUiThread {
                val data = Intent().putExtra("error", message)
                setResult(RESULT_CANCELED, data)
                finish()
            }
        }

        @JavascriptInterface
        fun onDismiss() {
            runOnUiThread {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }
}

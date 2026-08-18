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

// Loads Razorpay's own hosted checkout.js inside a WebView. Mirrors the exact fixes already
// proven to work in the WebView-based EngTeck APK's MainActivity: (1) stripping the ";
// wv" and "Version/x.x" tokens from the User-Agent so Razorpay treats this like a real
// Chrome browser and shows UPI, and (2) parsing "intent://" URIs properly via
// Intent.parseUri(URI_INTENT_SCHEME) — Razorpay's UPI app links are Android Intent-URIs, not
// plain "upi://" links, so a generic Uri.parse() + ACTION_VIEW silently does nothing.
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

        // Strip both UA tokens that reveal "this is a WebView, not real Chrome" — Razorpay
        // hides UPI when it detects either one.
        val defaultUA = webView.settings.userAgentString
        val cleanedUA = defaultUA
            .replace("; wv", "")
            .replace(Regex("Version/[0-9.]+\\s+"), "")
        webView.settings.userAgentString = cleanedUA

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val scheme = url.scheme ?: ""

                if (scheme == "http" || scheme == "https") {
                    // Razorpay's own checkout pages — keep loading inside this WebView.
                    return false
                }

                // Razorpay's UPI payment flow generates "intent://" links (Android's Intent-URI
                // format), not plain "upi://" links. Intent.parseUri() with URI_INTENT_SCHEME is
                // required to correctly read the target package/action/fallback baked into the
                // string — without this, tapping GPay/PhonePe/Paytm silently does nothing.
                if (scheme == "intent") {
                    return try {
                        val realIntent = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME)
                        startActivity(realIntent)
                        true
                    } catch (e: Exception) {
                        // Target UPI app isn't installed — Razorpay embeds a
                        // "browser_fallback_url" (usually the app's Play Store page) inside the
                        // intent string; open that instead of doing nothing.
                        try {
                            val fallbackUrl = Regex("S\\.browser_fallback_url=([^;]+)")
                                .find(url.toString())?.groupValues?.get(1)
                            if (fallbackUrl != null) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Uri.decode(fallbackUrl))))
                            }
                        } catch (e2: Exception) {
                            // Nothing more we can do — app not installed and no usable fallback.
                        }
                        true
                    }
                }

                // Any other custom scheme (upi://, tez://, phonepe://, paytmmp:// etc.) —
                // hand it straight to Android to open in the matching app.
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                } catch (e: ActivityNotFoundException) {
                    true
                }
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

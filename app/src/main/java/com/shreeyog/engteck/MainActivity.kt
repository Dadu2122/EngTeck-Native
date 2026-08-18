package com.shreeyog.engteck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.razorpay.PaymentResultListener
import com.shreeyog.engteck.navigation.EngTeckNavGraph
import com.shreeyog.engteck.payment.RazorpayBridge
import com.shreeyog.engteck.ui.theme.EngTeckTheme

class MainActivity : ComponentActivity(), PaymentResultListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EngTeckTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EngTeckNavGraph()
                }
            }
        }
    }

    // Razorpay Checkout calls these back on the hosting Activity. We forward the result
    // through RazorpayBridge so whichever Compose screen opened the checkout (Registration,
    // PDF unlock, Video unlock, etc.) can react without MainActivity needing to know about it.
    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        RazorpayBridge.notifySuccess(razorpayPaymentId ?: "")
    }

    override fun onPaymentError(code: Int, response: String?) {
        RazorpayBridge.notifyError(code, response ?: "Payment failed")
    }
}

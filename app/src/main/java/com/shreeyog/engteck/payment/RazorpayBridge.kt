package com.shreeyog.engteck.payment

// MainActivity implements Razorpay's PaymentResultListener (required by the SDK — it can only
// call back an Activity, not a Composable directly). This object is the relay: a Compose screen
// registers its success/error callbacks here right before opening Checkout, and MainActivity's
// onPaymentSuccess/onPaymentError forward the result through to whichever screen is waiting.
object RazorpayBridge {
    private var onSuccess: ((String) -> Unit)? = null
    private var onError: ((Int, String) -> Unit)? = null

    fun setCallbacks(onSuccess: (String) -> Unit, onError: (Int, String) -> Unit) {
        this.onSuccess = onSuccess
        this.onError = onError
    }

    fun clearCallbacks() {
        onSuccess = null
        onError = null
    }

    fun notifySuccess(paymentId: String) {
        val cb = onSuccess
        clearCallbacks()
        cb?.invoke(paymentId)
    }

    fun notifyError(code: Int, response: String) {
        val cb = onError
        clearCallbacks()
        cb?.invoke(code, response)
    }
}

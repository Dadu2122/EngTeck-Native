package com.shreeyog.engteck.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.FirebaseDatabase
import com.shreeyog.engteck.payment.RazorpayBridge
import com.shreeyog.engteck.payment.createRazorpayOrder
import com.shreeyog.engteck.payment.openRazorpayCheckout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

// Same pricing tables as the web app (PLAN_PRICING_WITH_VIDEOS / _WITHOUT_VIDEOS,
// PLAN_DURATION_OPTIONS / _NO_LIVE) — duration in months -> price in rupees.
private val PLAN_PRICING_WITH_VIDEOS = mapOf(
    "tgt" to mapOf(1 to 1500, 2 to 2500, 3 to 4500),
    "pgt" to mapOf(1 to 1999, 2 to 2999, 5 to 5999),
    "lt" to mapOf(1 to 1999, 2 to 2999, 5 to 5999),
    "gic" to mapOf(1 to 1999, 2 to 2999, 5 to 5999)
)
private val PLAN_DURATION_OPTIONS_LIVE = mapOf(
    "tgt" to listOf(1 to "1 Month", 2 to "2 Months", 3 to "3 Months (Full Course)"),
    "pgt" to listOf(1 to "1 Month", 2 to "2 Months", 5 to "5 Months (Full Course)"),
    "lt" to listOf(1 to "1 Month", 2 to "2 Months", 5 to "5 Months (Full Course)"),
    "gic" to listOf(1 to "1 Month", 2 to "2 Months", 5 to "5 Months (Full Course)")
)
private val PLAN_PRICING_WITHOUT_VIDEOS = mapOf(
    "tgt" to mapOf(1 to 799, 2 to 1499, 3 to 2099),
    "pgt" to mapOf(1 to 1099, 2 to 2099, 3 to 2999),
    "lt" to mapOf(1 to 1099, 2 to 2099, 3 to 2999),
    "gic" to mapOf(1 to 1099, 2 to 2099, 3 to 2999)
)
private val PLAN_DURATION_OPTIONS_NO_LIVE = mapOf(
    "tgt" to listOf(1 to "1 Month", 2 to "2 Months", 3 to "3 Months"),
    "pgt" to listOf(1 to "1 Month", 2 to "2 Months", 3 to "3 Months"),
    "lt" to listOf(1 to "1 Month", 2 to "2 Months", 3 to "3 Months"),
    "gic" to listOf(1 to "1 Month", 2 to "2 Months", 3 to "3 Months")
)
private val EXAM_TO_CATKEY = mapOf("TGT" to "tgt", "PGT" to "pgt", "LT" to "lt", "GIC Lecturer" to "gic")

@Composable
fun RegistrationFormCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var exam by remember { mutableStateOf("") }
    var examDropdownOpen by remember { mutableStateOf(false) }

    var planType by remember { mutableStateOf("") } // "withVideos" | "withoutVideos"
    var duration by remember { mutableStateOf<Int?>(null) }
    var durationDropdownOpen by remember { mutableStateOf(false) }

    var paid by remember { mutableStateOf(false) }
    var payingInProgress by remember { mutableStateOf(false) }
    var payMsg by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    val examOptions = listOf("TGT", "PGT", "LT", "GIC Lecturer", "UPPSC", "UPHESC")
    val catKey = EXAM_TO_CATKEY[exam]
    val durationOptions = when {
        catKey == null -> emptyList()
        planType == "withVideos" -> PLAN_DURATION_OPTIONS_LIVE[catKey] ?: emptyList()
        planType == "withoutVideos" -> PLAN_DURATION_OPTIONS_NO_LIVE[catKey] ?: emptyList()
        else -> emptyList()
    }
    val amount: Int? = if (catKey != null && duration != null) {
        val table = if (planType == "withVideos") PLAN_PRICING_WITH_VIDEOS else PLAN_PRICING_WITHOUT_VIDEOS
        table[catKey]?.get(duration)
    } else null

    // Reset paid state whenever any pricing-affecting field changes, so a stale "paid" flag
    // from a previous amount can never be reused for a different plan.
    fun resetPaymentState() {
        paid = false
        payMsg = ""
    }

    fun resetPlanFields() {
        planType = ""
        duration = null
        resetPaymentState()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
        Text("JOIN NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, color = Color(0xFF1B6B79))
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF6FA3D8), Color(0xFF3B6EA8), Color(0xFF2A5487))),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, Color(0xFF2A5487), RoundedCornerShape(12.dp))
                .padding(vertical = 13.dp)
        ) {
            Text("Registration Form", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth().background(Color.White).border(1.5.dp, Color(0xFFD4A017)).padding(18.dp)
        ) {
            RegField("Aspirant Name", true, name) { name = it }
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF9CA3AF), RoundedCornerShape(10.dp)).padding(vertical = 10.dp)
            ) {
                Text("⚠️ Show My Real Name Publicly", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            RegField("Nickname (Progress List पर यही दिखेगा)", true, nickname) { nickname = it }
            Spacer(Modifier.height(4.dp))
            Text(
                "आपका असली नाम कभी public नहीं दिखेगा। असली नाम सिर्फ Payment Receipt पर अंकित होगा। Progress Analytics और Leaderboard पर हमेशा यही Nickname दिखेगा।",
                fontSize = 11.sp, color = Color(0xFF5B5F6B), lineHeight = 15.sp
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { RegField("State", true, state) { state = it } }
                Box(Modifier.weight(1f)) { RegField("District", true, district) { district = it } }
            }
            Spacer(Modifier.height(14.dp))

            RegField("Mobile", true, mobile, keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone) {
                mobile = it.filter { c -> c.isDigit() }.take(10)
                resetPaymentState()
            }
            Spacer(Modifier.height(14.dp))
            RegField("Email", false, email, keyboardType = androidx.compose.ui.text.input.KeyboardType.Email) { email = it }
            Spacer(Modifier.height(14.dp))

            Text("Select Exam *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
            Spacer(Modifier.height(6.dp))
            Box {
                OutlinedButton(onClick = { examDropdownOpen = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text(if (exam.isEmpty()) "Choose one" else exam, color = Color(0xFF1A1A1A))
                }
                DropdownMenu(expanded = examDropdownOpen, onDismissRequest = { examDropdownOpen = false }) {
                    examOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            exam = option
                            examDropdownOpen = false
                            resetPlanFields()
                        })
                    }
                }
            }

            if (catKey != null) {
                Spacer(Modifier.height(14.dp))
                Text("Plan Type *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("withVideos" to "Live Class", "withoutVideos" to "No Live Class").forEach { (key, label) ->
                        val active = planType == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) Color(0xFFD4A017) else Color(0xFFF5F3EC), RoundedCornerShape(12.dp))
                                .border(1.5.dp, if (active) Color(0xFFD4A017) else Color(0xFFE3DFD3), RoundedCornerShape(12.dp))
                                .clickable {
                                    planType = key
                                    duration = null
                                    resetPaymentState()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF12203D))
                        }
                    }
                }

                if (durationOptions.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("Select Plan Duration", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(onClick = { durationDropdownOpen = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text(durationOptions.find { it.first == duration }?.second ?: "Choose one", color = Color(0xFF1A1A1A))
                        }
                        DropdownMenu(expanded = durationDropdownOpen, onDismissRequest = { durationDropdownOpen = false }) {
                            durationOptions.forEach { (v, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    duration = v
                                    durationDropdownOpen = false
                                    resetPaymentState()
                                })
                            }
                        }
                    }
                }

                if (amount != null) {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFAF8F3), RoundedCornerShape(14.dp))
                            .border(1.5.dp, Color(0xFFD4A017), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text("Amount to Pay", fontSize = 12.sp, color = Color(0xFF5B5F6B))
                        Text("₹$amount", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (paid) "Payment verified ✓" else "Payment वेरीफाई होते ही registration तुरंत कन्फर्म हो जाएगी।",
                            fontSize = 12.sp,
                            color = if (paid) Color(0xFF1F7A3D) else Color(0xFF5B5F6B)
                        )
                        Spacer(Modifier.height(10.dp))

                        if (!paid) {
                            Button(
                                onClick = {
                                    if (mobile.length != 10) {
                                        payMsg = "पहले ऊपर अपना 10-digit mobile number भर दो।"
                                        return@Button
                                    }
                                    val activity = context as? Activity
                                    if (activity == null) {
                                        payMsg = "Payment शुरू नहीं हो पाया। दोबारा कोशिश करो।"
                                        return@Button
                                    }
                                    payingInProgress = true
                                    payMsg = "Loading payment window…"
                                    val regKey = "reg_" + System.currentTimeMillis()
                                    val examLabel = exam
                                    scope.launch {
                                        val order = createRazorpayOrder(
                                            amount = amount,
                                            bookKey = regKey,
                                            mobile = mobile,
                                            title = "Registration — $examLabel"
                                        )
                                        if (order == null) {
                                            payingInProgress = false
                                            payMsg = "Instant payment अभी सेट अप नहीं हुआ या server से जवाब नहीं मिला। थोड़ी देर बाद ट्राई करो।"
                                            return@launch
                                        }
                                        RazorpayBridge.setCallbacks(
                                            onSuccess = {
                                                payMsg = "Payment successful! Confirming…"
                                                scope.launch {
                                                    var attemptsLeft = 20
                                                    var confirmed = false
                                                    while (attemptsLeft > 0 && !confirmed) {
                                                        val snap = FirebaseDatabase.getInstance()
                                                            .getReference("paidMiniBooks").child(mobile).child(regKey)
                                                            .getValueOnceOrNull()
                                                        if (snap == true) {
                                                            confirmed = true
                                                            paid = true
                                                            payingInProgress = false
                                                            payMsg = "Payment confirmed ✓"
                                                        } else {
                                                            attemptsLeft--
                                                            delay(2000)
                                                        }
                                                    }
                                                    if (!confirmed) {
                                                        payingInProgress = false
                                                        payMsg = "Payment कन्फर्म होने में ज़्यादा time लग रहा है — थोड़ी देर बाद दोबारा ट्राई करो।"
                                                    }
                                                }
                                            },
                                            onError = { _, _ ->
                                                payingInProgress = false
                                                payMsg = "Payment window बंद हो गई या fail हो गई। दोबारा \"Pay Now\" दबाओ।"
                                            }
                                        )
                                        openRazorpayCheckout(
                                            activity = activity,
                                            order = order,
                                            mobile = mobile,
                                            description = "Registration — $examLabel",
                                            coachingName = "Shree English Classes"
                                        )
                                    }
                                },
                                enabled = !payingInProgress,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F7A3D)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Text(if (payingInProgress) "Please wait..." else "⚡ Pay Now — Instant Verify", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (payMsg.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(payMsg, fontSize = 11.5.sp, color = if (paid) Color(0xFF1F7A3D) else Color(0xFFC0392B))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (name.isBlank() || nickname.isBlank() || state.isBlank() || district.isBlank() || mobile.isBlank() || exam.isBlank()) {
                        statusMsg = "Please fill all required fields"
                        return@Button
                    }
                    if (catKey != null && !paid) {
                        statusMsg = "पहले payment complete karo — 'Pay Now' dabao."
                        return@Button
                    }
                    submitting = true
                    statusMsg = ""

                    val entry = mutableMapOf<String, Any>(
                        "name" to name, "nickname" to nickname, "state" to state, "district" to district,
                        "mobile" to mobile, "email" to email, "exam" to exam,
                        "paid" to (catKey == null || paid), "timestamp" to System.currentTimeMillis()
                    )
                    if (catKey != null && duration != null && amount != null) {
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.MONTH, duration!!)
                        val expiryDisplay = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(cal.time)
                        val expiryIso = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(cal.time)
                        entry["planCategory"] = catKey
                        entry["planType"] = if (planType == "withVideos") "Live Class" else "No Live Class"
                        entry["planDurationMonths"] = duration.toString()
                        entry["planAmount"] = amount
                        entry["planExpiry"] = expiryDisplay
                        entry["planExpiryISO"] = expiryIso
                    }

                    FirebaseDatabase.getInstance().getReference("registrations").push().setValue(entry)
                        .addOnSuccessListener {
                            submitting = false
                            statusMsg = "Registration submitted ✓"
                            name = ""; nickname = ""; state = ""; district = ""; mobile = ""; email = ""; exam = ""
                            resetPlanFields()
                        }
                        .addOnFailureListener {
                            submitting = false
                            statusMsg = "Something went wrong, please try again"
                        }
                },
                enabled = !submitting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D4C)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (submitting) "Submitting..." else "Register", fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (statusMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(statusMsg, fontSize = 12.sp, color = if (statusMsg.contains("✓")) Color(0xFF1F7A3D) else Color(0xFFC0392B))
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Already Registered? Login", color = Color(0xFF1A1A1A))
            }
        }
    }
}

@Composable
private fun RegField(
    label: String,
    required: Boolean,
    value: String,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    onChange: (String) -> Unit
) {
    Column {
        Text(if (required) "$label *" else label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

// Reads a Firebase value once as a suspend call (no kotlinx-coroutines-play-services dependency
// needed — just wraps the standard listener callbacks).
private suspend fun com.google.firebase.database.DatabaseReference.getValueOnceOrNull(): Any? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        this.get()
            .addOnSuccessListener { snapshot -> if (cont.isActive) cont.resume(snapshot.value, null) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null, null) }
    }

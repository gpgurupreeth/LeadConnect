package com.example.mobileapp.telecom

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Raw telephony telemetry captured during an active call attempt.
 */
data class RawCallObservation(
    val phoneNumber: String,
    val startTime: Long,
    val endTime: Long = 0L,
    val wasOffhook: Boolean = false,
    val observedStates: List<Int> = emptyList(),
    val durationSeconds: Int = 0
)

/**
 * SIM-calling implementation using Android Telephony APIs.
 *
 * Invariants & Lifecycle:
 * - Checks CALL_PHONE permission before initiating Intent.ACTION_CALL.
 * - Tracks telephony state transitions (IDLE, RINGING, OFFHOOK).
 * - Properly registers and unregisters telephony listeners (no callback memory leaks).
 */
class AndroidTelecomCaller(
    private val context: Context,
    private val telephonyManager: TelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
) : ICaller {

    private var currentObservation: RawCallObservation? = null
    private val stateHistory = mutableListOf<Int>()
    private var offhookDetected = false
    private var callStartTime = 0L

    private var activeCallback: Any? = null
    private var activePhoneStateListener: PhoneStateListener? = null

    override fun placeCall(phoneNumber: String): Boolean {
        val cleanPhone = phoneNumber.filter { it.isDigit() || it == '+' }
        if (cleanPhone.isBlank()) return false

        startObserving(cleanPhone)
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${Uri.encode(cleanPhone)}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${Uri.encode(cleanPhone)}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
            true
        } catch (e: SecurityException) {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${Uri.encode(cleanPhone)}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                true
            } catch (fallbackEx: Exception) {
                stopObserving()
                false
            }
        } catch (e: Exception) {
            stopObserving()
            false
        }
    }

    fun finishCurrentCallObservation(): RawCallObservation {
        val endTime = System.currentTimeMillis()
        val duration = if (callStartTime > 0 && endTime > callStartTime) {
            ((endTime - callStartTime) / 1000).toInt()
        } else 0

        val obs = RawCallObservation(
            phoneNumber = currentObservation?.phoneNumber ?: "",
            startTime = callStartTime,
            endTime = endTime,
            wasOffhook = offhookDetected,
            observedStates = stateHistory.toList(),
            durationSeconds = duration
        )
        stopObserving()
        return obs
    }

    private fun startObserving(phoneNumber: String) {
        stopObserving()
        stateHistory.clear()
        offhookDetected = false
        callStartTime = System.currentTimeMillis()
        currentObservation = RawCallObservation(phoneNumber = phoneNumber, startTime = callStartTime)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    recordState(state)
                }
            }
            activeCallback = callback
            try {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } catch (e: Exception) {
                // SecurityException or unsupported
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    recordState(state)
                }
            }
            activePhoneStateListener = listener
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: Exception) {
                // SecurityException or unsupported
            }
        }
    }

    private fun recordState(state: Int) {
        stateHistory.add(state)
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            offhookDetected = true
        }
    }

    fun stopObserving() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activeCallback?.let { callback ->
                try {
                    telephonyManager.unregisterTelephonyCallback(callback as TelephonyCallback)
                } catch (e: Exception) {
                    // Ignore
                }
                activeCallback = null
            }
        } else {
            activePhoneStateListener?.let { listener ->
                try {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
                } catch (e: Exception) {
                    // Ignore
                }
                activePhoneStateListener = null
            }
        }
    }
}

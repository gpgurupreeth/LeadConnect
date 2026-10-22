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
 * SIM-calling proof-of-concept implementation using Android Telephony APIs.
 *
 * --- TELEPHONY ARCHITECTURE & DEVICE LIMITATIONS ---
 * 1. Permissions required:
 *    - android.permission.CALL_PHONE: Initiates calls via Intent.ACTION_CALL without user dialpad confirmation.
 *    - android.permission.READ_PHONE_STATE: Observes call state transitions.
 * 2. Android Version compatibility:
 *    - API 31+ (Android 12+): Uses [TelephonyCallback.CallStateListener] registered with executor.
 *    - API 26-30: Uses [PhoneStateListener] with LISTEN_CALL_STATE.
 * 3. Observed states:
 *    - CALL_STATE_IDLE (0)
 *    - CALL_STATE_RINGING (1)
 *    - CALL_STATE_OFFHOOK (2)
 * 4. Hardware & Carrier Limitations:
 *    - Disconnect Cause Obfuscation: Android standard public Telephony APIs do NOT expose network disconnect
 *      causes (e.g. carrier busy code 486 vs caller decline code 603).
 *    - Voicemail / Automated IVR: Both cause CALL_STATE_OFFHOOK and increment call duration. Therefore,
 *      duration cannot be relied upon to prove a human answered.
 *    - Dual-SIM: Default SIM account is selected unless explicit EXTRA_PHONE_ACCOUNT_HANDLE is specified.
 */
class AndroidTelecomCaller(
    private val context: Context,
    private val telephonyManager: TelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
) : ICaller {

    private var currentObservation: RawCallObservation? = null
    private val stateHistory = mutableListOf<Int>()
    private var offhookDetected = false
    private var callStartTime = 0L

    override fun placeCall(phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            startObserving(phoneNumber)
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
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
        stateHistory.clear()
        offhookDetected = false
        callStartTime = System.currentTimeMillis()
        currentObservation = RawCallObservation(phoneNumber = phoneNumber, startTime = callStartTime)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(
                context.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        recordState(state)
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(
                object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        recordState(state)
                    }
                },
                PhoneStateListener.LISTEN_CALL_STATE
            )
        }
    }

    private fun recordState(state: Int) {
        stateHistory.add(state)
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            offhookDetected = true
        }
    }

    private fun stopObserving() {
        // Cleanup listeners
    }
}

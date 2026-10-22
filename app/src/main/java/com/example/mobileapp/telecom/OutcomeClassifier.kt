package com.example.mobileapp.telecom

import com.example.mobileapp.domain.model.CallOutcome

/**
 * Classifies call outcomes from Android Telephony events.
 *
 * NOTE: Due to Android Telephony API limitations, standard TelephonyManager / PhoneStateListener
 * callbacks only expose state transitions (IDLE -> OFFHOOK -> IDLE) without network-level
 * disconnect causes (e.g. busy tone vs caller hangup vs operator reject vs voicemail).
 * Duration alone is NOT a definitive indicator of ANSWERED (e.g., voicemail or automated IVRs
 * also increment duration).
 * Therefore, ambiguous transitions are classified as UNKNOWN requiring agent verification.
 */
object OutcomeClassifier {
    fun classify(
        callDurationSeconds: Int,
        wasOffhook: Boolean,
        userConfirmedAnswered: Boolean? = null
    ): CallOutcome {
        if (userConfirmedAnswered == true) {
            return CallOutcome.ANSWERED
        }
        if (userConfirmedAnswered == false) {
            return CallOutcome.NO_ANSWER
        }

        return when {
            !wasOffhook -> CallOutcome.UNKNOWN
            callDurationSeconds <= 0 -> CallOutcome.UNKNOWN
            else -> CallOutcome.UNKNOWN // Ambiguous due to carrier / IVR behaviors, requires confirmation
        }
    }
}

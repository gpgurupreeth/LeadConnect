package com.example.mobileapp.telecom

/**
 * Safe Demo Mode Caller that simulates call placement without touching cellular radio
 * or placing real SIM telephone calls.
 */
class DemoSimCaller(
    var onCallSimulated: ((String) -> Unit)? = null
) : ICaller {

    var simulatedCallsCount = 0
        private set

    var lastSimulatedNumber: String? = null
        private set

    override fun placeCall(phoneNumber: String): Boolean {
        simulatedCallsCount++
        lastSimulatedNumber = phoneNumber
        onCallSimulated?.invoke(phoneNumber)
        return true
    }

    fun reset() {
        simulatedCallsCount = 0
        lastSimulatedNumber = null
    }
}

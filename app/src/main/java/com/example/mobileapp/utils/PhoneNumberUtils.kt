package com.example.mobileapp.utils

data class PhoneVerificationResult(
    val isValid: Boolean,
    val normalizedNumber: String,
    val formattedDisplay: String,
    val isDummyOrFake: Boolean = false,
    val issueReason: String? = null
)

object PhoneNumberUtils {
    private val DUMMY_PATTERNS = setOf(
        "0000000000", "1111111111", "2222222222", "3333333333", "4444444444",
        "5555555555", "6666666666", "7777777777", "8888888888", "9999999999",
        "1234567890", "0123456789"
    )

    /**
     * Normalizes a phone number to standard E.164 format.
     */
    fun normalize(input: String, defaultCountryCode: String = "+91"): String {
        val clean = input.replace(Regex("[^0-9+]"), "")
        return when {
            clean.startsWith("+") -> clean
            clean.startsWith("00") -> "+" + clean.substring(2)
            clean.length == 10 -> "$defaultCountryCode$clean"
            clean.length == 11 && clean.startsWith("0") -> "$defaultCountryCode" + clean.substring(1)
            clean.length in 7..9 -> "$defaultCountryCode$clean"
            else -> clean
        }
    }

    fun isValid(input: String): Boolean {
        val normalized = normalize(input)
        return normalized.matches(Regex("^\\+[1-9]\\d{6,14}\$"))
    }

    /**
     * Deep verification of phone numbers: checks length, E.164 structure, and dummy/fake patterns.
     */
    fun verify(input: String, defaultCountryCode: String = "+91"): PhoneVerificationResult {
        val rawDigits = input.replace(Regex("[^0-9]"), "")
        if (rawDigits.length < 10) {
            return PhoneVerificationResult(
                isValid = false,
                normalizedNumber = input.trim(),
                formattedDisplay = input.trim(),
                issueReason = "Incomplete (${rawDigits.length} digits, minimum 10 needed)"
            )
        }

        val last10 = rawDigits.takeLast(10)
        if (DUMMY_PATTERNS.contains(last10) || last10.all { it == last10[0] }) {
            return PhoneVerificationResult(
                isValid = false,
                normalizedNumber = normalize(input, defaultCountryCode),
                formattedDisplay = formatForDisplay(normalize(input, defaultCountryCode)),
                isDummyOrFake = true,
                issueReason = "Dummy / fake test pattern"
            )
        }

        val normalized = normalize(input, defaultCountryCode)
        if (!isValid(normalized)) {
            return PhoneVerificationResult(
                isValid = false,
                normalizedNumber = normalized,
                formattedDisplay = normalized,
                issueReason = "Invalid telephone number format"
            )
        }

        return PhoneVerificationResult(
            isValid = true,
            normalizedNumber = normalized,
            formattedDisplay = formatForDisplay(normalized)
        )
    }

    fun formatForDisplay(number: String): String {
        val digits = number.replace(Regex("[^0-9+]"), "")
        if (digits.startsWith("+91") && digits.length == 13) {
            return "+91 ${digits.substring(3, 8)} ${digits.substring(8)}"
        }
        return number
    }
}

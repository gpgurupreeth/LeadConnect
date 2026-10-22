package com.example.mobileapp.utils

object PhoneNumberUtils {
    /**
     * Normalizes a phone number to standard format (removes spaces, hyphens, parentheses).
     * Defaults to prepending default country code if missing.
     */
    fun normalize(input: String, defaultCountryCode: String = "+91"): String {
        val clean = input.replace(Regex("[^0-9+]"), "")
        return when {
            clean.startsWith("+") -> clean
            clean.startsWith("00") -> "+" + clean.substring(2)
            clean.length == 10 -> "$defaultCountryCode$clean"
            else -> clean
        }
    }

    fun isValid(input: String): Boolean {
        val normalized = normalize(input)
        return normalized.matches(Regex("^\\+[1-9]\\d{6,14}\$"))
    }
}

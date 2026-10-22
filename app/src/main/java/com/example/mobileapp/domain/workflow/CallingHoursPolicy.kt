package com.example.mobileapp.domain.workflow

import java.util.Calendar

/**
 * Enforces calling hours rules (e.g. 09:00 - 18:00) to ensure compliance with telemarketing regulations.
 */
data class CallingHoursConfig(
    val startHour: Int = 9,      // 09:00 (9 AM)
    val startMinute: Int = 0,
    val endHour: Int = 18,       // 18:00 (6 PM)
    val endMinute: Int = 0,
    val enforceCallingHours: Boolean = true
) {
    fun isWithinAllowedHours(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        if (!enforceCallingHours) return true
        val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        val startTotalMinutes = startHour * 60 + startMinute
        val endTotalMinutes = endHour * 60 + endMinute

        return currentTotalMinutes in startTotalMinutes until endTotalMinutes
    }

    fun formattedRange(): String {
        val startFormatted = String.format("%02d:%02d", startHour, startMinute)
        val endFormatted = String.format("%02d:%02d", endHour, endMinute)
        return "$startFormatted - $endFormatted"
    }
}

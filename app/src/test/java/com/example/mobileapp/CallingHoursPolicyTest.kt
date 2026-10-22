package com.example.mobileapp

import com.example.mobileapp.domain.workflow.CallingHoursConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CallingHoursPolicyTest {

    @Test
    fun `test within allowed hours returns true`() {
        val policy = CallingHoursConfig(startHour = 9, endHour = 18, enforceCallingHours = true)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14) // 2:00 PM
            set(Calendar.MINUTE, 30)
        }

        assertTrue(policy.isWithinAllowedHours(cal.timeInMillis))
    }

    @Test
    fun `test outside allowed hours returns false`() {
        val policy = CallingHoursConfig(startHour = 9, endHour = 18, enforceCallingHours = true)

        val calNight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21) // 9:00 PM
            set(Calendar.MINUTE, 0)
        }

        assertFalse(policy.isWithinAllowedHours(calNight.timeInMillis))

        val calEarlyMorning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7) // 7:00 AM
            set(Calendar.MINUTE, 30)
        }

        assertFalse(policy.isWithinAllowedHours(calEarlyMorning.timeInMillis))
    }

    @Test
    fun `test enforce false allows calling at any hour`() {
        val policy = CallingHoursConfig(startHour = 9, endHour = 18, enforceCallingHours = false)

        val calMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
        }

        assertTrue(policy.isWithinAllowedHours(calMidnight.timeInMillis))
    }

    @Test
    fun `test formatted range string`() {
        val policy = CallingHoursConfig(startHour = 9, startMinute = 0, endHour = 18, endMinute = 0)
        assertEquals("09:00 - 18:00", policy.formattedRange())
    }
}

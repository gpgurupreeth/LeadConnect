package com.example.mobileapp

import com.example.mobileapp.utils.PhoneNumberUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {

    @Test
    fun `normalizes 10 digit number with default country code`() {
        val result = PhoneNumberUtils.normalize("9876543210", defaultCountryCode = "+91")
        assertEquals("+919876543210", result)
    }

    @Test
    fun `normalizes phone number with dashes, brackets and spaces`() {
        val result = PhoneNumberUtils.normalize("+91 (987) 65-43210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun `normalizes international 00 prefix to plus`() {
        val result = PhoneNumberUtils.normalize("00919876543210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun `preserves existing valid E164 format`() {
        val result = PhoneNumberUtils.normalize("+14155552671")
        assertEquals("+14155552671", result)
    }

    @Test
    fun `validates valid and invalid numbers correctly`() {
        assertTrue(PhoneNumberUtils.isValid("+919876543210"))
        assertTrue(PhoneNumberUtils.isValid("+14155552671"))
        assertFalse(PhoneNumberUtils.isValid("123"))
        assertFalse(PhoneNumberUtils.isValid("abc"))
    }
}

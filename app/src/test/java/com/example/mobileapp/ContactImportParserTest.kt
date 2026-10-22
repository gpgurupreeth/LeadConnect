package com.example.mobileapp

import com.example.mobileapp.utils.ContactImportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactImportParserTest {

    @Test
    fun `test CSV with headers parses correctly and normalizes to E164`() {
        val csv = """
            Name,Phone,Company,Notes
            Rahul Sharma,9876543210,Apex Retail,High priority
            Priya Patel,+919876543211,Zenith Logistics,Key account
        """.trimIndent()

        val summary = ContactImportParser.parseCsv(csv, defaultCountryCode = "+91")

        assertEquals(2, summary.totalProcessed)
        assertEquals(2, summary.validCount)
        assertEquals(0, summary.invalidCount)
        assertEquals(0, summary.duplicateCount)

        val first = summary.parsedContacts[0]
        assertEquals("Rahul Sharma", first.name)
        assertEquals("+919876543210", first.phoneNumber)
        assertEquals("Apex Retail", first.company)
        assertEquals("High priority", first.notes)
        assertTrue(first.isValid)
    }

    @Test
    fun `test invalid numbers are caught and flagged`() {
        val csv = """
            Phone
            12345
            invalid_phone
            9876543210
        """.trimIndent()

        val summary = ContactImportParser.parseCsv(csv, defaultCountryCode = "+91")

        assertEquals(3, summary.totalProcessed)
        assertEquals(1, summary.validCount)
        assertEquals(2, summary.invalidCount)

        val invalid1 = summary.parsedContacts[0]
        assertFalse(invalid1.isValid)
        assertTrue(invalid1.validationError!!.contains("Invalid phone format"))
    }

    @Test
    fun `test duplicates within batch are deduplicated`() {
        val csv = """
            Name,Phone
            Lead One,9876543210
            Lead Two,9876543210
            Lead Three,+919876543210
        """.trimIndent()

        val summary = ContactImportParser.parseCsv(csv, defaultCountryCode = "+91")

        assertEquals(3, summary.totalProcessed)
        assertEquals(1, summary.validCount)
        assertEquals(2, summary.duplicateCount)
    }

    @Test
    fun `test suppression preservation ensures imported numbers never clear DND status`() {
        val csv = """
            Name,Phone
            OptedOut Customer,9876543299
            Regular Customer,9876543211
        """.trimIndent()

        val suppressedSet = setOf("+919876543299")

        val summary = ContactImportParser.parseCsv(csv, defaultCountryCode = "+91", suppressedNumbers = suppressedSet)

        assertEquals(2, summary.validCount)
        assertEquals(1, summary.suppressedCount)

        val optedOut = summary.parsedContacts.first { it.phoneNumber == "+919876543299" }
        assertTrue("Import must preserve suppression and never silently clear it", optedOut.isSuppressed)

        val regular = summary.parsedContacts.first { it.phoneNumber == "+919876543211" }
        assertFalse(regular.isSuppressed)
    }

    @Test
    fun `test raw text paste with one number per line`() {
        val rawText = """
            9876543210
            9876543211
            9876543212
        """.trimIndent()

        val summary = ContactImportParser.parseRawText(rawText, defaultCountryCode = "+91")

        assertEquals(3, summary.totalProcessed)
        assertEquals(3, summary.validCount)
        assertEquals("+919876543210", summary.parsedContacts[0].phoneNumber)
    }

    @Test
    fun `test semicolon and tab delimited formats`() {
        val semicolonData = """
            Name;Phone;Company
            Anand Verma;9876543212;Quantum Tech
        """.trimIndent()

        val summary = ContactImportParser.parseCsv(semicolonData, defaultCountryCode = "+91")

        assertEquals(1, summary.validCount)
        assertEquals("Anand Verma", summary.parsedContacts[0].name)
        assertEquals("+919876543212", summary.parsedContacts[0].phoneNumber)
        assertEquals("Quantum Tech", summary.parsedContacts[0].company)
    }
}

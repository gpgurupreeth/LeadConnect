package com.example.mobileapp.utils

data class ParsedContact(
    val name: String,
    val phoneNumber: String,
    val company: String? = null,
    val notes: String? = null,
    val isValid: Boolean = true,
    val isDuplicate: Boolean = false,
    val isSuppressed: Boolean = false,
    val validationError: String? = null
)

data class ImportSummary(
    val totalProcessed: Int,
    val validCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int,
    val suppressedCount: Int,
    val parsedContacts: List<ParsedContact>
)

object ContactImportParser {

    /**
     * Parses CSV or delimited text content into structured contacts with normalization,
     * deduplication, and suppression preservation.
     */
    fun parseCsv(
        content: String,
        defaultCountryCode: String = "+91",
        suppressedNumbers: Set<String> = emptySet()
    ): ImportSummary {
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return ImportSummary(0, 0, 0, 0, 0, emptyList())
        }

        val parsedList = mutableListOf<ParsedContact>()
        val seenNumbers = mutableSetOf<String>()

        // Check if first line is a header
        val firstLineLower = lines[0].lowercase()
        val hasHeader = firstLineLower.contains("phone") ||
                firstLineLower.contains("mobile") ||
                firstLineLower.contains("number") ||
                firstLineLower.contains("name")

        val dataLines = if (hasHeader) lines.drop(1) else lines

        // Determine column indexes if header present
        var nameCol = -1
        var phoneCol = -1
        var companyCol = -1
        var notesCol = -1

        if (hasHeader) {
            val headerCols = splitRow(lines[0])
            for (i in headerCols.indices) {
                val col = headerCols[i].lowercase()
                when {
                    phoneCol == -1 && (col.contains("phone") || col.contains("mobile") || col.contains("contact") || col.contains("number") || col.contains("tel")) -> phoneCol = i
                    nameCol == -1 && (col.contains("name") || col.contains("lead") || col.contains("customer")) -> nameCol = i
                    companyCol == -1 && (col.contains("company") || col.contains("org") || col.contains("business")) -> companyCol = i
                    notesCol == -1 && (col.contains("note") || col.contains("tag") || col.contains("comment") || col.contains("remark")) -> notesCol = i
                }
            }
        }

        for (line in dataLines) {
            val cols = splitRow(line)
            if (cols.isEmpty()) continue

            var rawPhone = ""
            var name = ""
            var company: String? = null
            var notes: String? = null

            if (hasHeader && phoneCol != -1) {
                rawPhone = cols.getOrElse(phoneCol) { "" }
                name = if (nameCol != -1) cols.getOrElse(nameCol) { "" } else ""
                company = if (companyCol != -1) cols.getOrNull(companyCol)?.takeIf { it.isNotBlank() } else null
                notes = if (notesCol != -1) cols.getOrNull(notesCol)?.takeIf { it.isNotBlank() } else null
            } else {
                // Heuristic mapping by column count
                when (cols.size) {
                    1 -> {
                        rawPhone = cols[0]
                    }
                    2 -> {
                        // Check which column looks like a number
                        if (looksLikePhone(cols[0])) {
                            rawPhone = cols[0]
                            name = cols[1]
                        } else {
                            name = cols[0]
                            rawPhone = cols[1]
                        }
                    }
                    3 -> {
                        name = cols[0]
                        rawPhone = cols[1]
                        company = cols[2].takeIf { it.isNotBlank() }
                    }
                    else -> {
                        name = cols[0]
                        rawPhone = cols[1]
                        company = cols[2].takeIf { it.isNotBlank() }
                        notes = cols.drop(3).joinToString("; ").takeIf { it.isNotBlank() }
                    }
                }
            }

            val verification = PhoneNumberUtils.verify(rawPhone, defaultCountryCode)
            val normalized = verification.normalizedNumber
            val isValid = verification.isValid

            if (!isValid) {
                parsedList.add(
                    ParsedContact(
                        name = if (name.isNotBlank()) name else "Invalid Contact",
                        phoneNumber = if (rawPhone.isNotBlank()) rawPhone else "N/A",
                        company = company,
                        notes = notes,
                        isValid = false,
                        isDuplicate = false,
                        isSuppressed = false,
                        validationError = verification.issueReason ?: "Invalid phone number format"
                    )
                )
                continue
            }

            // Deduplication
            val isDuplicate = seenNumbers.contains(normalized)
            if (isDuplicate) {
                parsedList.add(
                    ParsedContact(
                        name = if (name.isNotBlank()) name else "Contact ${normalized.takeLast(4)}",
                        phoneNumber = normalized,
                        company = company,
                        notes = notes,
                        isValid = true,
                        isDuplicate = true,
                        isSuppressed = suppressedNumbers.contains(normalized),
                        validationError = "Duplicate phone number in import batch"
                    )
                )
                continue
            }
            seenNumbers.add(normalized)

            // Check suppression preservation
            val isSuppressed = suppressedNumbers.contains(normalized)

            parsedList.add(
                ParsedContact(
                    name = if (name.isNotBlank()) name else "Contact ${normalized.takeLast(4)}",
                    phoneNumber = normalized,
                    company = company,
                    notes = notes,
                    isValid = true,
                    isDuplicate = false,
                    isSuppressed = isSuppressed,
                    validationError = null
                )
            )
        }

        val validCount = parsedList.count { it.isValid && !it.isDuplicate }
        val duplicateCount = parsedList.count { it.isDuplicate }
        val invalidCount = parsedList.count { !it.isValid }
        val suppressedCount = parsedList.count { it.isSuppressed }

        return ImportSummary(
            totalProcessed = parsedList.size,
            validCount = validCount,
            duplicateCount = duplicateCount,
            invalidCount = invalidCount,
            suppressedCount = suppressedCount,
            parsedContacts = parsedList
        )
    }

    /**
     * Parses plain pasted text where each line could be just a phone number,
     * or a comma/tab delimited row.
     */
    fun parseRawText(
        text: String,
        defaultCountryCode: String = "+91",
        suppressedNumbers: Set<String> = emptySet()
    ): ImportSummary {
        return parseCsv(text, defaultCountryCode, suppressedNumbers)
    }

    private fun splitRow(row: String): List<String> {
        val delimiter = when {
            row.contains("\t") -> '\t'
            row.contains(";") -> ';'
            else -> ','
        }

        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in row) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    tokens.add(current.toString().trim(' ', '\"', '\''))
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        tokens.add(current.toString().trim(' ', '\"', '\''))
        return tokens
    }

    private fun looksLikePhone(s: String): Boolean {
        val digits = s.filter { it.isDigit() }
        return digits.length >= 7
    }
}

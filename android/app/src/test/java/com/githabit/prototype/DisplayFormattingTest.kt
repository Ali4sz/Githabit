package com.githabit.prototype

import com.githabit.prototype.ui.dateLabel
import com.githabit.prototype.ui.formatAmount
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DisplayFormattingTest {
    @Test fun explicitDisplayLocaleDoesNotFollowProcessDefault() {
        assertEquals("1,234.125", formatAmount(1234.125, Locale.US))
        assertEquals("1.234,125", formatAmount(1234.125, Locale.GERMANY))
        val day = LocalDate.parse("2026-11-04")
        assertNotEquals(dateLabel(day, Locale.US), dateLabel(day, Locale.GERMANY))
        assertEquals("2026-11-04", day.toString())
    }

    @Test fun rawInputsRetainDocumentedDotOrCommaDecimalContract() {
        assertEquals(1.25, parseFormAmount("1.25"), 0.0)
        assertEquals(1.25, parseFormAmount("1,25"), 0.0)
        for (input in listOf("1,234.5", "1.234,5", "1 234,5")) {
            assertThrows(IllegalArgumentException::class.java) { parseFormAmount(input) }
        }
        assertEquals("1.25", editableFormAmount(1.25))
    }
}

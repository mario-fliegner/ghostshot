// path: app/src/test/java/com/isardomains/sameview/ui/wackelbild/DateBadgeFormatterTest.kt
package com.isardomains.sameview.ui.wackelbild

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DateBadgeFormatterTest {

    private fun expectedYearMonth(year: Int, month1Based: Int, locale: Locale): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1Based - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("MMM yyyy", locale).format(calendar.time)
    }

    private fun expectedFullDate(year: Int, month1Based: Int, day: Int, locale: Locale): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1Based - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        return SimpleDateFormat("d MMM yyyy", locale).format(calendar.time)
    }

    // --- Reference precision ---

    @Test
    fun formatReferenceDate_yearOnly_returnsYearAlone() {
        val result = DateBadgeFormatter.formatReferenceDate("2008", Locale.US)
        assertEquals("2008", result)
    }

    @Test
    fun formatReferenceDate_yearMonth_matchesLocaleFormattedYearMonth() {
        val result = DateBadgeFormatter.formatReferenceDate("2008-06", Locale.US)
        assertEquals(expectedYearMonth(2008, 6, Locale.US), result)
    }

    @Test
    fun formatReferenceDate_yearMonth_neverInventsDay() {
        // "MMM yyyy" structurally has no day-of-month field, so the expected fixture itself
        // already proves no day is invented; this asserts the formatter actually uses that
        // pattern rather than silently falling back to a full-date format.
        val result = DateBadgeFormatter.formatReferenceDate("2008-06", Locale.US)
        assertEquals(expectedYearMonth(2008, 6, Locale.US), result)
        assertNotEquals(expectedFullDate(2008, 6, 1, Locale.US), result)
    }

    @Test
    fun formatReferenceDate_fullDate_matchesLocaleFormattedFullDate() {
        val result = DateBadgeFormatter.formatReferenceDate("2008-06-15", Locale.US)
        assertEquals(expectedFullDate(2008, 6, 15, Locale.US), result)
    }

    // --- Malformed / empty / null input ---

    @Test
    fun formatReferenceDate_null_returnsNull() {
        assertNull(DateBadgeFormatter.formatReferenceDate(null, Locale.US))
    }

    @Test
    fun formatReferenceDate_empty_returnsNull() {
        assertNull(DateBadgeFormatter.formatReferenceDate("", Locale.US))
    }

    @Test
    fun formatReferenceDate_blank_returnsNull() {
        assertNull(DateBadgeFormatter.formatReferenceDate("   ", Locale.US))
    }

    @Test
    fun formatReferenceDate_nonNumericYear_returnsNull() {
        assertNull(DateBadgeFormatter.formatReferenceDate("abcd", Locale.US))
    }

    @Test
    fun formatReferenceDate_invalidMonth_returnsNull() {
        assertNull(DateBadgeFormatter.formatReferenceDate("2008-13", Locale.US))
    }

    @Test
    fun formatReferenceDate_invalidDay_returnsNull() {
        assertNull(DateBadgeFormatter.formatReferenceDate("2008-06-99", Locale.US))
    }

    @Test
    fun formatReferenceDate_tooShortNonYearLength_returnsNull() {
        // Neither a valid year-only (length 4) nor long enough for year-month (>= 7).
        assertNull(DateBadgeFormatter.formatReferenceDate("20-06", Locale.US))
    }

    // --- Locale coverage ---

    @Test
    fun formatReferenceDate_german_yearMonth_matchesLocaleFormattedYearMonth() {
        val result = DateBadgeFormatter.formatReferenceDate("2008-06", Locale.GERMANY)
        assertEquals(expectedYearMonth(2008, 6, Locale.GERMANY), result)
    }

    @Test
    fun formatReferenceDate_german_vs_english_yearMonth_differ() {
        val english = DateBadgeFormatter.formatReferenceDate("2008-06", Locale.US)
        val german = DateBadgeFormatter.formatReferenceDate("2008-06", Locale.GERMANY)
        assertEquals(expectedYearMonth(2008, 6, Locale.US), english)
        assertEquals(expectedYearMonth(2008, 6, Locale.GERMANY), german)
    }

    @Test
    fun formatReferenceDate_german_fullDate_matchesExpected() {
        val result = DateBadgeFormatter.formatReferenceDate("2008-06-15", Locale.GERMANY)
        assertEquals(expectedFullDate(2008, 6, 15, Locale.GERMANY), result)
    }

    // --- Capture date ---

    @Test
    fun formatCaptureDate_validTimestamp_returnsFullDate() {
        val fixed = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 3)
        }.timeInMillis

        val result = DateBadgeFormatter.formatCaptureDate(fixed, Locale.US)
        assertEquals(expectedFullDate(2026, 8, 3, Locale.US), result)
    }

    @Test
    fun formatCaptureDate_zeroTimestamp_returnsNull_noInventedDate() {
        assertNull(DateBadgeFormatter.formatCaptureDate(0L, Locale.US))
    }

    @Test
    fun formatCaptureDate_negativeTimestamp_returnsNull() {
        assertNull(DateBadgeFormatter.formatCaptureDate(-1L, Locale.US))
    }

    @Test
    fun formatCaptureDate_noTimeOfDayInOutput() {
        val fixed = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 3)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 37)
        }.timeInMillis

        val result = DateBadgeFormatter.formatCaptureDate(fixed, Locale.US)
        assertEquals(expectedFullDate(2026, 8, 3, Locale.US), result)
        assertFalse(result!!.contains(":"))
        assertFalse(result.contains("AM") || result.contains("PM"))
    }
}

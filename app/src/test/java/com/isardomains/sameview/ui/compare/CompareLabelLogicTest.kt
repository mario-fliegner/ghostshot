// path: app/src/test/java/com/isardomains/sameview/ui/compare/CompareLabelLogicTest.kt
package com.isardomains.sameview.ui.compare

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class CompareLabelLogicTest {

    private val locale = Locale.ENGLISH
    private val past = "Past"
    private val present = "Present"
    private val reference = "Reference"
    private val current = "Current"

    // ── Level 5 ─────────────────────────────────────────────────────────────

    @Test
    fun level5_nullReferenceDate_returnsRoleLabels() {
        val result = computeCompareLabels(
            referenceDate = null,
            captureTimestampMs = makeTimestamp(2026, Calendar.JUNE, 10),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals(reference, result.left)
        assertEquals(current, result.right)
    }

    @Test
    fun level5_nullReferenceDate_ignoresCaptureTimestamp() {
        val result = computeCompareLabels(
            referenceDate = null,
            captureTimestampMs = 0L,
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals(reference, result.left)
        assertEquals(current, result.right)
    }

    // ── Level 1 ─────────────────────────────────────────────────────────────

    @Test
    fun level1_yearOnlyPrecision_differentYears() {
        val result = computeCompareLabels(
            referenceDate = "2008",
            captureTimestampMs = makeTimestamp(2026, Calendar.JUNE, 10),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals("2008", result.left)
        assertEquals("2026", result.right)
    }

    @Test
    fun level1_monthPrecision_differentYears() {
        val result = computeCompareLabels(
            referenceDate = "2008-06",
            captureTimestampMs = makeTimestamp(2026, Calendar.JUNE, 10),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals("2008", result.left)
        assertEquals("2026", result.right)
    }

    @Test
    fun level1_dayPrecision_differentYears() {
        val result = computeCompareLabels(
            referenceDate = "2008-06-15",
            captureTimestampMs = makeTimestamp(2026, Calendar.OCTOBER, 28),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals("2008", result.left)
        assertEquals("2026", result.right)
    }

    // ── Level 2 ─────────────────────────────────────────────────────────────

    @Test
    fun level2_sameYear_differentMonths_monthPrecision() {
        val result = computeCompareLabels(
            referenceDate = "2026-03",
            captureTimestampMs = makeTimestamp(2026, Calendar.OCTOBER, 1),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals("Mar 2026", result.left)
        assertEquals("Oct 2026", result.right)
    }

    @Test
    fun level2_sameYear_differentMonths_dayPrecision() {
        val result = computeCompareLabels(
            referenceDate = "2026-03-15",
            captureTimestampMs = makeTimestamp(2026, Calendar.OCTOBER, 28),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals("Mar 2026", result.left)
        assertEquals("Oct 2026", result.right)
    }

    @Test
    fun level2_yearOnlyPrecision_sameYear_fallsThrough_toLevel4() {
        // Year-only precision: cannot detect month difference → Level 4
        val result = computeCompareLabels(
            referenceDate = "2026",
            captureTimestampMs = makeTimestamp(2026, Calendar.OCTOBER, 28),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals(past, result.left)
        assertEquals(present, result.right)
    }

    // ── Level 3 ─────────────────────────────────────────────────────────────

    @Test
    fun level3_sameYear_sameMonth_differentDays_dayPrecision() {
        val result = computeCompareLabels(
            referenceDate = "2026-06-12",
            captureTimestampMs = makeTimestamp(2026, Calendar.JUNE, 28),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals("12 Jun", result.left)
        assertEquals("28 Jun", result.right)
    }

    @Test
    fun level3_monthPrecision_sameYearMonth_fallsThrough_toLevel4() {
        // Month precision: cannot detect day difference → Level 4
        val result = computeCompareLabels(
            referenceDate = "2026-06",
            captureTimestampMs = makeTimestamp(2026, Calendar.JUNE, 28),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals(past, result.left)
        assertEquals(present, result.right)
    }

    // ── Level 4 ─────────────────────────────────────────────────────────────

    @Test
    fun level4_yearPrecision_sameYear() {
        val result = computeCompareLabels(
            referenceDate = "2026",
            captureTimestampMs = makeTimestamp(2026, Calendar.JUNE, 10),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals(past, result.left)
        assertEquals(present, result.right)
    }

    @Test
    fun level4_monthPrecision_sameYearAndMonth() {
        val result = computeCompareLabels(
            referenceDate = "2026-06",
            captureTimestampMs = makeTimestamp(2026, Calendar.JUNE, 10),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals(past, result.left)
        assertEquals(present, result.right)
    }

    @Test
    fun level3_sameYear_sameMonth_sameDays_dayPrecision() {
        val result = computeCompareLabels(
            referenceDate = "2026-06-10",
            captureTimestampMs = makeTimestamp(2026, Calendar.JUNE, 10),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals("10 Jun", result.left)
        assertEquals("10 Jun", result.right)
    }

    // ── Precision boundary ───────────────────────────────────────────────────

    @Test
    fun precision_length4_yearOnly() {
        // "2020" — year precision; same year as capture → Level 4
        val result = computeCompareLabels(
            referenceDate = "2020",
            captureTimestampMs = makeTimestamp(2020, Calendar.JANUARY, 1),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals(past, result.left)
        assertEquals(present, result.right)
    }

    @Test
    fun precision_length7_monthPrecision_differentYear_isLevel1() {
        val result = computeCompareLabels(
            referenceDate = "2019-12",
            captureTimestampMs = makeTimestamp(2026, Calendar.JANUARY, 5),
            locale = locale,
            labelPast = past, labelPresent = present,
            labelReference = reference, labelCurrent = current
        )
        assertEquals("2019", result.left)
        assertEquals("2026", result.right)
    }

    @Test
    fun locale_german_level2_usesGermanMonthAbbreviation() {
        val deLocale = Locale.GERMAN
        val result = computeCompareLabels(
            referenceDate = "2026-03",
            captureTimestampMs = makeTimestamp(2026, Calendar.OCTOBER, 1),
            locale = deLocale,
            labelPast = "Früher", labelPresent = "Heute",
            labelReference = "Referenz", labelCurrent = "Aktuell"
        )
        // German "Mär" or "März" for March; "Okt" for October — just verify not English
        val left = result.left
        val right = result.right
        assert(left.contains("2026")) { "Expected year in left label: $left" }
        assert(right.contains("2026")) { "Expected year in right label: $right" }
        // German should NOT contain English "Mar" or "Oct"
        assert(!left.contains("Mar")) { "German label should not contain English 'Mar': $left" }
        assert(!right.contains("Oct")) { "German label should not contain English 'Oct': $right" }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Builds a UTC epoch millisecond value for the given calendar date (local time zone). */
    private fun makeTimestamp(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

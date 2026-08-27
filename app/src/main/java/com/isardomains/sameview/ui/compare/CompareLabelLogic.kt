// path: app/src/main/java/com/isardomains/sameview/ui/compare/CompareLabelLogic.kt
package com.isardomains.sameview.ui.compare

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Left and right text labels shown adjacent to the compare slider handle.
 */
data class CompareLabelPair(val left: String, val right: String)

/**
 * Computes compare slider labels according to the five-level priority chain defined in
 * COMPARE_FLOW_V1.md §41.4.
 *
 * Pure function: no Compose dependency, no Android Context dependency.
 *
 * @param referenceDate ISO 8601 date at year ("YYYY"), month ("YYYY-MM"), or day
 *   ("YYYY-MM-DD") precision; null when absent from metadata.json.
 * @param captureTimestampMs Unix epoch milliseconds for the capture timestamp.
 * @param locale Locale used for locale-aware date formatting in levels 2 and 3.
 * @param labelPast   Level 4 left label (string resource value).
 * @param labelPresent Level 4 right label.
 * @param labelReference Level 5 left label.
 * @param labelCurrent   Level 5 right label.
 */
fun computeCompareLabels(
    referenceDate: String?,
    captureTimestampMs: Long,
    locale: Locale,
    labelPast: String,
    labelPresent: String,
    labelReference: String,
    labelCurrent: String
): CompareLabelPair {
    // Level 5 — no reference.date
    if (referenceDate == null) {
        return CompareLabelPair(labelReference, labelCurrent)
    }

    val refYear = parseDateYear(referenceDate)
    val capCal = Calendar.getInstance().apply { timeInMillis = captureTimestampMs }
    val capYear = capCal.get(Calendar.YEAR)

    // Level 1 — different years
    if (refYear != capYear) {
        return CompareLabelPair(refYear.toString(), capYear.toString())
    }

    // Level 2 — same year, different months, month precision available
    if (referenceDate.length >= 7) {
        val refMonth = parseDateMonth(referenceDate) // 0-based
        val capMonth = capCal.get(Calendar.MONTH)
        if (refMonth != capMonth) {
            val fmt = SimpleDateFormat("MMM yyyy", locale)
            val refCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, refYear)
                set(Calendar.MONTH, refMonth)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            return CompareLabelPair(fmt.format(refCal.time), fmt.format(capCal.time))
        }
    }

    // Level 3 — same year, same month, day precision available (includes same-day)
    if (referenceDate.length >= 10) {
        val fmt = SimpleDateFormat("d MMM", locale)
        val refMonth = parseDateMonth(referenceDate)
        val refDay = parseDateDay(referenceDate)
        val refCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, refYear)
            set(Calendar.MONTH, refMonth)
            set(Calendar.DAY_OF_MONTH, refDay)
        }
        return CompareLabelPair(fmt.format(refCal.time), fmt.format(capCal.time))
    }

    // Level 4 — reference.date present but dates indistinguishable at available precision
    return CompareLabelPair(labelPast, labelPresent)
}

private fun parseDateYear(date: String): Int = date.substring(0, 4).toInt()

/** Returns 0-based month index parsed from YYYY-MM or YYYY-MM-DD string. */
private fun parseDateMonth(date: String): Int = date.substring(5, 7).toInt() - 1

private fun parseDateDay(date: String): Int = date.substring(8, 10).toInt()

/**
 * True when [referenceDate] is strictly later than the capture date derived from
 * [captureTimestampMs], compared only at the precision actually present in [referenceDate]
 * (`SESSION_METADATA_EDITOR_V1.md §9` — "Reference Date Must Not Be After Capture Date").
 *
 * Comparison is precision-aware, never inventing missing month/day components: a year-only
 * [referenceDate] is compared by year alone (the same year as capture is never "after"), a
 * year-month value by year and month, and a full date by year, month, and day (same day is
 * never "after").
 *
 * The capture date is derived using the device's local/default timezone
 * (`Calendar.getInstance()`), identical to the convention already used by
 * [computeCompareLabels] above and by the displayed "Captured on" date elsewhere in the app —
 * this function intentionally does not introduce a UTC interpretation.
 *
 * Assumes [referenceDate] is already structurally valid (`SessionStorage.isValidReferenceDate`);
 * this function performs no format validation of its own.
 */
fun isReferenceDateAfterCapture(referenceDate: String, captureTimestampMs: Long): Boolean {
    val capCal = Calendar.getInstance().apply { timeInMillis = captureTimestampMs }

    val refYear = parseDateYear(referenceDate)
    val capYear = capCal.get(Calendar.YEAR)
    if (refYear != capYear) return refYear > capYear
    if (referenceDate.length < 7) return false

    val refMonth = parseDateMonth(referenceDate)
    val capMonth = capCal.get(Calendar.MONTH)
    if (refMonth != capMonth) return refMonth > capMonth
    if (referenceDate.length < 10) return false

    val refDay = parseDateDay(referenceDate)
    val capDay = capCal.get(Calendar.DAY_OF_MONTH)
    return refDay > capDay
}

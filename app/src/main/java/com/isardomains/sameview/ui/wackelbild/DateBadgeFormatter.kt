// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/DateBadgeFormatter.kt
package com.isardomains.sameview.ui.wackelbild

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Small pure helper that formats the two Wackelbild date-badge strings. No Android [android.content.Context]
 * dependency — locale is always passed in explicitly (see [com.isardomains.sameview.ui.compare.ShareComparisonViewModel]'s
 * `currentUiLocale` precedent, which this feature reuses the pattern of rather than `Locale.getDefault()`).
 *
 * Reuses the same length-based precision technique already established by
 * [com.isardomains.sameview.ui.compare.CompareLabelLogic] (4 → year, >=7 → year+month, >=10 → full
 * date) without touching that file's private helpers — this is a small enough duplication that
 * extracting a shared utility would not be worth the coupling.
 */
object DateBadgeFormatter {

    /**
     * Formats `reference.date` (a nullable ISO-style string at year/month/day precision) at
     * exactly the precision present in the stored value. Never invents a missing month or day.
     * Returns null when [referenceDate] is null, empty, or too malformed to parse safely.
     */
    fun formatReferenceDate(referenceDate: String?, locale: Locale): String? {
        val date = referenceDate?.trim()
        if (date.isNullOrEmpty()) return null
        return when {
            date.length >= 10 -> formatFullDate(date, locale)
            date.length >= 7 -> formatYearMonth(date, locale)
            date.length == 4 -> formatYearOnly(date)
            else -> null
        }
    }

    /**
     * Formats the Capture date from `capture.timestampMs` as a full localized date, no
     * time-of-day. Returns null for a zero/negative (missing/unusable) timestamp — never
     * substitutes the current device date or a file timestamp.
     */
    fun formatCaptureDate(captureTimestampMs: Long, locale: Locale): String? {
        if (captureTimestampMs <= 0L) return null
        return try {
            SimpleDateFormat("d MMM yyyy", locale).format(Date(captureTimestampMs))
        } catch (_: Exception) {
            null
        }
    }

    private fun formatYearOnly(date: String): String? {
        val year = date.substring(0, 4).toIntOrNull() ?: return null
        return year.toString()
    }

    private fun formatYearMonth(date: String, locale: Locale): String? {
        val year = date.substring(0, 4).toIntOrNull() ?: return null
        val month = date.substring(5, 7).toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return try {
            val calendar = Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            SimpleDateFormat("MMM yyyy", locale).format(calendar.time)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatFullDate(date: String, locale: Locale): String? {
        val year = date.substring(0, 4).toIntOrNull() ?: return null
        val month = date.substring(5, 7).toIntOrNull() ?: return null
        val day = date.substring(8, 10).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return try {
            val calendar = Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
            }
            SimpleDateFormat("d MMM yyyy", locale).format(calendar.time)
        } catch (_: Exception) {
            null
        }
    }
}

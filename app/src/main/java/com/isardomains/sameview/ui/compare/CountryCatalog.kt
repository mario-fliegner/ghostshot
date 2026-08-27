// path: app/src/main/java/com/isardomains/sameview/ui/compare/CountryCatalog.kt
package com.isardomains.sameview.ui.compare

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * One selectable country: its ISO 3166-1 alpha-2 code and its display name localized for a
 * specific [Locale].
 *
 * [code] is always uppercase. [displayName] is the localized country name, or [code] itself when
 * no localized name is available for the requested locale (deterministic last-resort fallback per
 * `SESSION_METADATA_EDITOR_V1.md §10.5`).
 */
internal data class CountryEntry(
    val code: String,
    val displayName: String
)

/**
 * Pure, non-Compose country data source for the Country picker (`CountryPickerSheet`).
 *
 * Uses only platform/JVM locale APIs (`Locale.getISOCountries()`, `Locale.getDisplayCountry()`,
 * `Collator`, `Normalizer`) — fully offline, no network, no external dataset, no new dependency.
 */
internal object CountryCatalog {

    private val ISO_CODES: Set<String> by lazy { Locale.getISOCountries().toSet() }

    /**
     * True only for an exact, case-sensitive match against the real ISO 3166-1 alpha-2 set
     * (`Locale.getISOCountries()`, which yields uppercase codes only). A lowercase or otherwise
     * malformed persisted code (e.g. `"de"`, `"ZZZ"`) is never normalized or repaired here — it is
     * simply invalid, and callers fall back to the stored `country` text (`SESSION_METADATA_V1.md
     * §6.9.8`). A non-blank [displayNameFor] result is not proof of validity by itself: the JDK
     * returns a deterministic "Unknown Region"-style string for some unrecognized codes, which is
     * why validity is checked against the real ISO set rather than inferred from lookup success.
     */
    fun isValidCode(code: String): Boolean = code in ISO_CODES

    /**
     * Resolves the user-facing Country display text for a stored [country] snapshot and/or
     * [countryCode], for the given display [locale].
     *
     * Contract (`SESSION_METADATA_V1.md §6.9.7`, `SESSION_METADATA_EDITOR_V1.md §10`):
     * - [countryCode] valid -> localized name for [locale], regardless of [country]'s content or
     *   language (a stored snapshot in a different language is never shown once a valid code
     *   exists)
     * - [countryCode] missing/invalid/unresolvable -> [country] exactly as stored (trimmed,
     *   blank treated as absent)
     * - both absent -> `null`
     *
     * Never mutates its inputs or any persisted state; never infers or backfills a code from
     * [country]'s text. [locale] must always be passed explicitly by the caller — this function
     * never reads `Locale.getDefault()` itself.
     */
    fun resolveDisplayName(country: String?, countryCode: String?, locale: Locale): String? {
        if (countryCode != null && isValidCode(countryCode)) {
            return displayNameFor(countryCode, locale)
        }
        return country?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns every ISO 3166-1 alpha-2 country, with display names localized for [locale] and
     * sorted alphabetically per [locale]'s collation rules.
     */
    fun countries(locale: Locale): List<CountryEntry> {
        val collator = Collator.getInstance(locale)
        return Locale.getISOCountries()
            .map { code -> CountryEntry(code = code, displayName = displayNameFor(code, locale)) }
            .sortedWith(compareBy(collator) { it.displayName })
    }

    /**
     * Localized display name for a single ISO 3166-1 alpha-2 [code], or [code] itself if no
     * localized name is available for [locale].
     */
    fun displayNameFor(code: String, locale: Locale): String {
        val name = Locale("", code).getDisplayCountry(locale)
        return name.ifBlank { code }
    }

    /**
     * Filters [countries] by [query], per `SESSION_METADATA_EDITOR_V1.md §10.2`:
     * - empty/blank query returns the full list unchanged, in its existing order
     * - matching is case-insensitive, diacritic-insensitive, and starts from the first character
     * - display-name matches rank above ISO-code matches
     * - within display-name matches, a name starting with [query] ranks above one that only
     *   contains it elsewhere
     * - each entry appears at most once, in its highest-ranked tier
     */
    fun filter(countries: List<CountryEntry>, query: String): List<CountryEntry> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return countries

        val normalizedQuery = normalizeForSearch(trimmedQuery)

        val nameStartsWith = mutableListOf<CountryEntry>()
        val nameContains = mutableListOf<CountryEntry>()
        val codeMatches = mutableListOf<CountryEntry>()

        for (entry in countries) {
            val normalizedName = normalizeForSearch(entry.displayName)
            when {
                normalizedName.startsWith(normalizedQuery) -> nameStartsWith.add(entry)
                normalizedName.contains(normalizedQuery) -> nameContains.add(entry)
                entry.code.startsWith(trimmedQuery, ignoreCase = true) -> codeMatches.add(entry)
            }
        }

        return nameStartsWith + nameContains + codeMatches
    }

    /**
     * Lowercases and strips combining diacritical marks (via NFD normalization) so that, for
     * example, "Osterreich" and "Österreich" compare equal for search purposes.
     */
    private fun normalizeForSearch(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}

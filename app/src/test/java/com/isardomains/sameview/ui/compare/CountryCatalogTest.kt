// path: app/src/test/java/com/isardomains/sameview/ui/compare/CountryCatalogTest.kt
package com.isardomains.sameview.ui.compare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CountryCatalogTest {

    private val de = Locale("de")
    private val en = Locale("en")

    @Test
    fun countries_de_showsGermanDisplayNames() {
        val countries = CountryCatalog.countries(de)
        val germany = countries.first { it.code == "DE" }
        assertEquals("Deutschland", germany.displayName)
    }

    @Test
    fun countries_en_showsEnglishDisplayNames() {
        val countries = CountryCatalog.countries(en)
        val germany = countries.first { it.code == "DE" }
        assertEquals("Germany", germany.displayName)
    }

    @Test
    fun countries_sameCode_differsByLocale() {
        val germanyDe = CountryCatalog.countries(de).first { it.code == "DE" }
        val germanyEn = CountryCatalog.countries(en).first { it.code == "DE" }
        assertEquals("DE", germanyDe.code)
        assertEquals("DE", germanyEn.code)
        assertTrue(germanyDe.displayName != germanyEn.displayName)
    }

    @Test
    fun countries_includesBasicIsoSanitySet() {
        val codes = CountryCatalog.countries(en).map { it.code }.toSet()
        // Basic completeness sanity check, not a brittle full-list assertion.
        assertTrue(codes.containsAll(listOf("DE", "AT", "US", "GB", "FR")))
    }

    @Test
    fun countries_areSortedAlphabeticallyByLocale() {
        val names = CountryCatalog.countries(en).map { it.displayName }
        val sorted = names.sortedWith(java.text.Collator.getInstance(en).let { collator ->
            Comparator { a, b -> collator.compare(a, b) }
        })
        assertEquals(sorted, names)
    }

    @Test
    fun displayNameFor_unknownCode_neverCrashes_neverReturnsBlank() {
        // "ZZ" is not a real ISO 3166-1 code and is never actually passed by countries(), which
        // only ever calls this with real codes from Locale.getISOCountries(). The JDK's own
        // fallback for a genuinely unrecognized region ("Unknown Region") already satisfies the
        // "no crash, deterministic" contract in SESSION_METADATA_EDITOR_V1.md §10.5 without this
        // function needing to override it; CountryCatalog.displayNameFor's own ISO-code fallback
        // exists as defensive insurance for the (untriggered-in-practice) blank case.
        val name = CountryCatalog.displayNameFor("ZZ", en)
        assertTrue(name.isNotBlank())
    }

    @Test
    fun displayNameFor_everyRealIsoCode_neverReturnsBlank() {
        // The realistic guarantee: for every actual ISO 3166-1 code, the fallback (whichever
        // form it takes) is never a blank string.
        Locale.getISOCountries().forEach { code ->
            assertTrue(
                "displayNameFor($code) returned blank",
                CountryCatalog.displayNameFor(code, en).isNotBlank()
            )
        }
    }

    @Test
    fun filter_blankQuery_returnsFullListUnchanged() {
        val all = CountryCatalog.countries(en)
        assertEquals(all, CountryCatalog.filter(all, ""))
        assertEquals(all, CountryCatalog.filter(all, "   "))
    }

    @Test
    fun filter_de_startsWithFirstCharacter_matchesDeutschland() {
        val all = CountryCatalog.countries(de)
        val result = CountryCatalog.filter(all, "deu")
        assertTrue(result.any { it.code == "DE" })
        assertEquals("Deutschland", result.first { it.code == "DE" }.displayName)
    }

    @Test
    fun filter_en_startsWithFirstCharacter_matchesGermany() {
        val all = CountryCatalog.countries(en)
        val result = CountryCatalog.filter(all, "ger")
        assertTrue(result.any { it.code == "DE" })
        assertEquals("Germany", result.first { it.code == "DE" }.displayName)
    }

    @Test
    fun filter_isCaseInsensitive() {
        val all = CountryCatalog.countries(en)
        val lower = CountryCatalog.filter(all, "germany")
        val upper = CountryCatalog.filter(all, "GERMANY")
        assertEquals(lower.map { it.code }, upper.map { it.code })
    }

    @Test
    fun filter_isDiacriticInsensitive() {
        val all = CountryCatalog.countries(de)
        // "Osterreich" (no umlaut) must still match "Österreich".
        val result = CountryCatalog.filter(all, "Osterreich")
        assertTrue(result.any { it.code == "AT" })
    }

    @Test
    fun filter_rendersStartsWithMatches_beforeContainsMatches() {
        val all = CountryCatalog.countries(en)
        // "united" is a contains-match for many names but a startsWith-match for a few
        // ("United States", "United Kingdom", "United Arab Emirates", ...).
        val result = CountryCatalog.filter(all, "united")
        val firstNonStartsWithIndex = result.indexOfFirst {
            !it.displayName.lowercase(en).startsWith("united")
        }
        val lastStartsWithIndex = result.indexOfLast {
            it.displayName.lowercase(en).startsWith("united")
        }
        assertTrue(firstNonStartsWithIndex == -1 || lastStartsWithIndex < firstNonStartsWithIndex)
    }

    @Test
    fun filter_isoCodeSecondaryMatch() {
        val all = CountryCatalog.countries(en)
        val result = CountryCatalog.filter(all, "DE")
        assertTrue(result.any { it.code == "DE" })
    }

    @Test
    fun filter_eachEntryAppearsAtMostOnce() {
        val all = CountryCatalog.countries(en)
        val result = CountryCatalog.filter(all, "a")
        assertEquals(result.size, result.map { it.code }.toSet().size)
    }

    // ── isValidCode / resolveDisplayName (Issue #2 — locale-aware display) ────────

    @Test
    fun isValidCode_realUppercaseCode_true() {
        assertTrue(CountryCatalog.isValidCode("DE"))
    }

    @Test
    fun isValidCode_lowercase_false_notNormalized() {
        // Lowercase is never repaired/normalized for validation — legacy malformed persisted
        // data falls back to the stored country, per SESSION_METADATA_V1.md §6.9.8.
        assertFalse(CountryCatalog.isValidCode("de"))
    }

    @Test
    fun isValidCode_wrongLength_false() {
        assertFalse(CountryCatalog.isValidCode("ZZZ"))
        assertFalse(CountryCatalog.isValidCode("D"))
    }

    @Test
    fun isValidCode_unassignedTwoLetterCode_false() {
        // "ZZ" is not a real ISO 3166-1 code even though it is two uppercase letters and even
        // though displayNameFor("ZZ", ...) returns a non-blank JDK fallback string — non-blank
        // lookup output is not proof of ISO validity.
        assertFalse(CountryCatalog.isValidCode("ZZ"))
    }

    @Test
    fun resolveDisplayName_validCode_de_returnsDeutschland() {
        assertEquals("Deutschland", CountryCatalog.resolveDisplayName("Germany", "DE", de))
    }

    @Test
    fun resolveDisplayName_validCode_en_returnsGermany() {
        assertEquals("Germany", CountryCatalog.resolveDisplayName("Germany", "DE", en))
    }

    @Test
    fun resolveDisplayName_storedNameInOtherLanguage_stillResolvesToCurrentLocale() {
        assertEquals("Germany", CountryCatalog.resolveDisplayName("Deutschland", "DE", en))
    }

    @Test
    fun resolveDisplayName_missingCode_returnsStoredCountryUnchanged() {
        assertEquals("Östereich", CountryCatalog.resolveDisplayName("Östereich", null, en))
    }

    @Test
    fun resolveDisplayName_invalidCode_returnsStoredCountryUnchanged() {
        assertEquals("Germany", CountryCatalog.resolveDisplayName("Germany", "ZZZ", de))
    }

    @Test
    fun resolveDisplayName_lowercaseCode_treatedAsInvalid_returnsStoredCountryUnchanged() {
        assertEquals("Germany", CountryCatalog.resolveDisplayName("Germany", "de", en))
    }

    @Test
    fun resolveDisplayName_validCode_missingStoredCountry_returnsLocalizedName() {
        assertEquals("Deutschland", CountryCatalog.resolveDisplayName(null, "DE", de))
    }

    @Test
    fun resolveDisplayName_neitherPresent_returnsNull() {
        assertEquals(null, CountryCatalog.resolveDisplayName(null, null, en))
    }

    @Test
    fun resolveDisplayName_blankStoredCountry_noCode_returnsNull() {
        assertEquals(null, CountryCatalog.resolveDisplayName("   ", null, en))
    }

    @Test
    fun resolveDisplayName_neverMutatesInputs() {
        val country = "Germany"
        val code = "DE"
        CountryCatalog.resolveDisplayName(country, code, de)
        assertEquals("Germany", country)
        assertEquals("DE", code)
    }
}

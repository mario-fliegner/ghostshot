// path: app/src/main/java/com/isardomains/sameview/ui/compare/CountryPickerSheet.kt
package com.isardomains.sameview.ui.compare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.theme.SameViewSettingsSecondaryText
import java.util.Locale

/**
 * ModalBottomSheet that presents the full ISO 3166-1 country list, localized for [currentLocale],
 * with local search/filter (`SESSION_METADATA_EDITOR_V1.md §10.1-§10.2`).
 *
 * The full alphabetically sorted list is visible immediately on open; no search input is required
 * to browse and select. Tapping a row calls [onCountrySelected] with the chosen [CountryEntry] and
 * closes the sheet. Dismissing without selecting (scrim tap, swipe, back gesture) calls [onDismiss]
 * and mutates nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountryPickerSheet(
    currentLocale: Locale,
    onCountrySelected: (CountryEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val allCountries = remember(currentLocale) { CountryCatalog.countries(currentLocale) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredCountries = remember(allCountries, query) { CountryCatalog.filter(allCountries, query) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.edit_session_country_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.edit_session_country_picker_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_session_country_picker_search")
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (allCountries.isEmpty()) {
                Text(
                    text = stringResource(R.string.edit_session_country_picker_empty),
                    color = SameViewSettingsSecondaryText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .testTag("edit_session_country_picker_empty_state")
                )
            } else if (filteredCountries.isEmpty()) {
                Text(
                    text = stringResource(R.string.edit_session_country_picker_empty),
                    color = SameViewSettingsSecondaryText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .testTag("edit_session_country_picker_no_results")
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .testTag("edit_session_country_picker_list"),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredCountries, key = { it.code }) { entry ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onCountrySelected(entry) }
                                .semantics { contentDescription = entry.displayName }
                                .testTag("edit_session_country_picker_row_${entry.code}"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = entry.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

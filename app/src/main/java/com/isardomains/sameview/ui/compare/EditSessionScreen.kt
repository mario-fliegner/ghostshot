package com.isardomains.sameview.ui.compare

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.settings.SettingsCard
import com.isardomains.sameview.ui.theme.SameViewSettingsLabelText
import com.isardomains.sameview.ui.theme.SameViewSettingsSecondaryText
import com.isardomains.sameview.ui.theme.SameViewStarFavorited
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Fullscreen editor screen for session metadata.
 *
 * Save button (sticky bottom bar) is enabled when [EditSessionViewModel.isDirty] is true and
 * [EditSessionViewModel.isSaving] is false. Clicking Save calls [EditSessionViewModel.onSave].
 * Save events ([EditSessionEvent]) are observed by the host (MainActivity), which handles
 * navigation and snackbar display. The ViewModel is created by the host and passed as a
 * required parameter.
 *
 * Back handling (system back and TopAppBar icon):
 * - [EditSessionViewModel.isSaving] true → saving-in-progress dialog; navigation blocked.
 * - [EditSessionViewModel.isDirty] true → discard-changes confirmation dialog.
 * - Otherwise → immediate back navigation via [onBack].
 *
 * @param sessionId The session being edited.
 * @param onBack Called when the user navigates back.
 * @param viewModel Created by MainActivity; owns all form state and save logic.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun EditSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: EditSessionViewModel,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier,
) {
    // ── Collect state ──────────────────────────────────────────────────────────
    val title by viewModel.titleField.collectAsStateWithLifecycle()
    val description by viewModel.descriptionField.collectAsStateWithLifecycle()
    val referenceDate by viewModel.referenceDateField.collectAsStateWithLifecycle()
    val referenceError by viewModel.referenceDateError.collectAsStateWithLifecycle()
    val locationDisplayName by viewModel.locationDisplayNameField.collectAsStateWithLifecycle()
    val locationCity by viewModel.locationCityField.collectAsStateWithLifecycle()
    val locationCountry by viewModel.locationCountryField.collectAsStateWithLifecycle()
    val locationCountryCode by viewModel.locationCountryCodeField.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val captureTimestampMs by viewModel.captureTimestampMs.collectAsStateWithLifecycle()
    val referenceSourceMetadataPreserved by viewModel.referenceSourceMetadataPreserved.collectAsStateWithLifecycle()

    // ── UI derivations ─────────────────────────────────────────────────────────
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val currentLocale = LocalConfiguration.current.locales.get(0)

    // Display-only: resolves the visible Country text from the canonical countryCode for the
    // current SameView UI locale, without ever touching the ViewModel's stored/dirty-tracked
    // country/countryCode state (SESSION_METADATA_V1.md §6.9.7). A locale change alone re-renders
    // this value but never marks the session dirty and is never written back on Save.
    val resolvedLocationCountry = remember(locationCountry, locationCountryCode, currentLocale) {
        CountryCatalog.resolveDisplayName(locationCountry, locationCountryCode, currentLocale) ?: ""
    }

    val referenceImageUri = remember(viewModel.sessionId) {
        Uri.fromFile(File(context.filesDir, "sessions/${viewModel.sessionId}/reference.jpg"))
    }

    val captureImageUri = remember(viewModel.sessionId) {
        Uri.fromFile(File(context.filesDir, "sessions/${viewModel.sessionId}/capture.jpg"))
    }

    val captureDateWithTime = remember(captureTimestampMs) {
        if (captureTimestampMs > 0L)
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(java.util.Date(captureTimestampMs))
        else ""
    }

    // ── Dialog state ───────────────────────────────────────────────────────────
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showSavingDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }

    // ── Back handling ──────────────────────────────────────────────────────────
    BackHandler(enabled = isSaving || isDirty) {
        if (isSaving) showSavingDialog = true
        else showDiscardDialog = true
    }

    // ── Discard dialog ─────────────────────────────────────────────────────────
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.edit_session_discard_dialog_title)) },
            text = { Text(stringResource(R.string.edit_session_discard_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text(stringResource(R.string.edit_session_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.edit_session_discard_cancel))
                }
            }
        )
    }

    // ── Saving-in-progress dialog ──────────────────────────────────────────────
    if (showSavingDialog) {
        AlertDialog(
            onDismissRequest = { showSavingDialog = false },
            title = { Text(stringResource(R.string.edit_session_saving_dialog_title)) },
            text = { Text(stringResource(R.string.edit_session_saving_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { showSavingDialog = false }) {
                    Text(stringResource(R.string.edit_session_saving_dialog_confirm))
                }
            }
        )
    }

    // ── Date picker dialog ─────────────────────────────────────────────────────
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = millis
                        val formatted = String.format(
                            Locale.US, "%04d-%02d-%02d",
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH) + 1,
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                        viewModel.onReferenceDateChanged(formatted)
                    }
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Screen scaffold ────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        modifier = modifier.testTag("edit_session_screen_root"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.edit_session_screen_title))
                        Text(
                            text = stringResource(R.string.edit_session_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = SameViewSettingsSecondaryText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSaving) showSavingDialog = true
                            else if (isDirty) showDiscardDialog = true
                            else onBack()
                        },
                        modifier = Modifier.testTag("edit_session_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.edit_session_back_content_description)
                        )
                    }
                },
                actions = {
                    val starDescription = stringResource(
                        if (isFavorite) R.string.compare_screen_favorite_remove
                        else R.string.compare_screen_favorite_mark
                    )
                    IconButton(
                        onClick = { viewModel.toggleFavorite() },
                        modifier = Modifier.testTag("edit_session_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = starDescription,
                            tint = if (isFavorite) SameViewStarFavorited
                                   else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) 680.dp else Dp.Unspecified)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = viewModel::onSave,
                        enabled = isDirty && !isSaving,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_session_save_button")
                    ) {
                        Text(stringResource(R.string.edit_session_save_changes))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) 680.dp else Dp.Unspecified)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // ── Session card ──────────────────────────────────────────────────
            SettingsCard(
                title = if (captureDateWithTime.isNotEmpty())
                    stringResource(R.string.edit_session_created, captureDateWithTime)
                else null
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = viewModel::onTitleChanged,
                    label = { Text(stringResource(R.string.edit_session_field_title)) },
                    placeholder = { Text(stringResource(R.string.edit_session_placeholder_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().testTag("edit_session_title_field")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = viewModel::onDescriptionChanged,
                    label = { Text(stringResource(R.string.edit_session_field_description)) },
                    placeholder = { Text(stringResource(R.string.edit_session_placeholder_description)) },
                    singleLine = false,
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().testTag("edit_session_description_field")
                )
            }

            // ── Reference photo card ──────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.edit_session_card_reference_photo)) {
                val painter = rememberAsyncImagePainter(
                    model = referenceImageUri,
                    imageLoader = context.imageLoader
                )
                Row(verticalAlignment = Alignment.Top) {
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialTheme.shapes.small)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = referenceDate,
                        onValueChange = viewModel::onReferenceDateChanged,
                        label = { Text(stringResource(R.string.edit_session_field_reference_date)) },
                        placeholder = { Text(stringResource(R.string.edit_session_placeholder_reference_date)) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = stringResource(R.string.edit_session_pick_date_content_description)
                                )
                            }
                        },
                        singleLine = true,
                        isError = referenceError != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.weight(1f).testTag("edit_session_reference_date_field")
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (referenceError != null) {
                    Text(
                        text = stringResource(R.string.edit_session_reference_date_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = stringResource(R.string.edit_session_reference_date_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = SameViewSettingsSecondaryText
                    )
                }
                if (referenceSourceMetadataPreserved) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.edit_session_reference_metadata_preserved_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = SameViewSettingsSecondaryText,
                        modifier = Modifier.testTag("edit_session_reference_metadata_preserved_hint")
                    )
                }
            }

            // ── Current photo card ────────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.edit_session_card_current_photo)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val capturePainter = rememberAsyncImagePainter(
                        model = captureImageUri,
                        imageLoader = context.imageLoader
                    )
                    androidx.compose.foundation.Image(
                        painter = capturePainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialTheme.shapes.small)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.edit_session_label_captured_on),
                                style = MaterialTheme.typography.labelSmall,
                                color = SameViewSettingsSecondaryText
                            )
                            if (captureDateWithTime.isNotEmpty()) {
                                Text(
                                    text = captureDateWithTime,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SameViewSettingsLabelText
                                )
                            }
                        }
                    }
                }
            }

            // ── Location card ─────────────────────────────────────────────────
            SettingsCard(title = stringResource(R.string.edit_session_card_location)) {
                OutlinedTextField(
                    value = locationDisplayName,
                    onValueChange = viewModel::onLocationDisplayNameChanged,
                    label = { Text(stringResource(R.string.edit_session_field_place_name)) },
                    placeholder = { Text(stringResource(R.string.edit_session_placeholder_place_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().testTag("edit_session_place_name_field")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = locationCity,
                    onValueChange = viewModel::onLocationCityChanged,
                    label = { Text(stringResource(R.string.edit_session_field_city)) },
                    placeholder = { Text(stringResource(R.string.edit_session_placeholder_city)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().testTag("edit_session_city_field")
                )
                Spacer(modifier = Modifier.height(8.dp))
                val countryInteractionSource = remember { MutableInteractionSource() }
                LaunchedEffect(countryInteractionSource) {
                    countryInteractionSource.interactions.collect { interaction ->
                        if (interaction is PressInteraction.Release) {
                            showCountryPicker = true
                        }
                    }
                }
                OutlinedTextField(
                    value = resolvedLocationCountry,
                    onValueChange = {},
                    readOnly = true,
                    interactionSource = countryInteractionSource,
                    label = { Text(stringResource(R.string.edit_session_field_country)) },
                    trailingIcon = {
                        if (resolvedLocationCountry.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onCountryCleared() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.edit_session_country_clear_content_description)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_session_country_field")
                )
            }
            } // inner Column

            if (showCountryPicker) {
                CountryPickerSheet(
                    currentLocale = currentLocale,
                    onCountrySelected = { entry ->
                        viewModel.onCountrySelected(entry.displayName, entry.code)
                        showCountryPicker = false
                    },
                    onDismiss = { showCountryPicker = false }
                )
            }
        } // outer Column
    }
}

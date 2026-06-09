package com.isardomains.sameview.ui.compare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isardomains.sameview.R

/**
 * Fullscreen editor screen for session metadata.
 *
 * Save button is enabled when [EditSessionViewModel.isDirty] is true and
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: EditSessionViewModel,
    modifier: Modifier = Modifier,
) {
    val title by viewModel.titleField.collectAsStateWithLifecycle()
    val referenceDate by viewModel.referenceDateField.collectAsStateWithLifecycle()
    val referenceError by viewModel.referenceDateError.collectAsStateWithLifecycle()
    val locationDisplayName by viewModel.locationDisplayNameField.collectAsStateWithLifecycle()
    val locationCity by viewModel.locationCityField.collectAsStateWithLifecycle()
    val locationCountry by viewModel.locationCountryField.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showSavingDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSaving || isDirty) {
        if (isSaving) showSavingDialog = true
        else showDiscardDialog = true
    }

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

    Scaffold(
        modifier = modifier.testTag("edit_session_screen_root"),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.edit_session_screen_title))
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
                    TextButton(
                        onClick = viewModel::onSave,
                        enabled = isDirty && !isSaving,
                        modifier = Modifier.testTag("edit_session_save_button")
                    ) {
                        Text(stringResource(R.string.edit_session_save))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text(stringResource(R.string.edit_session_field_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = referenceDate,
                onValueChange = viewModel::onReferenceDateChanged,
                label = { Text(stringResource(R.string.edit_session_field_reference_date)) },
                placeholder = { Text(stringResource(R.string.edit_session_reference_date_hint)) },
                singleLine = true,
                isError = referenceError != null,
                supportingText = if (referenceError != null) {
                    { Text(stringResource(R.string.edit_session_reference_date_error)) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = locationDisplayName,
                onValueChange = viewModel::onLocationDisplayNameChanged,
                label = { Text(stringResource(R.string.edit_session_field_location_display_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = locationCity,
                onValueChange = viewModel::onLocationCityChanged,
                label = { Text(stringResource(R.string.edit_session_field_city)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = locationCountry,
                onValueChange = viewModel::onLocationCountryChanged,
                label = { Text(stringResource(R.string.edit_session_field_country)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

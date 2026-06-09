package com.isardomains.sameview.ui.compare

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isardomains.sameview.R

/**
 * Fullscreen editor screen for session metadata.
 *
 * Block D: adds the Reference Date [OutlinedTextField] with validation error display.
 * Title field ImeAction updated to Next. No save logic or dirty-state tracking yet.
 *
 * @param sessionId The session being edited.
 * @param onBack Called when the user navigates back.
 * @param viewModel Injected by Hilt; replaceable in tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditSessionViewModel = hiltViewModel()
) {
    val title by viewModel.titleField.collectAsStateWithLifecycle()
    val referenceDate by viewModel.referenceDateField.collectAsStateWithLifecycle()
    val referenceError by viewModel.referenceDateError.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.testTag("edit_session_screen_root"),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.edit_session_screen_title))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
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
                        onClick = {},
                        enabled = false,
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

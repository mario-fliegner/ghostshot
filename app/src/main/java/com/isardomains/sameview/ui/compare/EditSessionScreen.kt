package com.isardomains.sameview.ui.compare

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.isardomains.sameview.R

/**
 * Fullscreen editor screen for session metadata.
 *
 * Block A shell: opaque screen with TopAppBar and navigation only.
 * No form fields, no ViewModel, no save logic in this block.
 *
 * @param sessionId The session being edited; passed through to subsequent blocks.
 * @param onBack Called when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            // Form fields will be added in subsequent blocks.
        }
    }
}

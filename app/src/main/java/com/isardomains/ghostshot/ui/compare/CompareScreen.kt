package com.isardomains.ghostshot.ui.compare

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isardomains.ghostshot.R

/**
 * Fullscreen shell for the compare flow.
 *
 * Step 2 only establishes the destination, back navigation, and defensive input handling.
 * The slider-based compare viewport is added in the following step.
 */
@Composable
fun CompareScreen(
    referenceImageUri: Uri?,
    captureImageUri: Uri?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasValidInput = referenceImageUri != null && captureImageUri != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("compare_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("compare_screen_top_bar"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("compare_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.compare_back)
                    )
                }
                Text(
                    text = stringResource(R.string.compare_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasValidInput) {
                    CompareScreenShellContent()
                } else {
                    CompareMissingInputFallback()
                }
            }
        }
    }
}

@Composable
private fun CompareScreenShellContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("compare_screen_shell_content")
    )
}

@Composable
private fun CompareMissingInputFallback() {
    Column(
        modifier = Modifier.testTag("compare_missing_input_fallback"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = stringResource(R.string.compare_error_missing_images),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

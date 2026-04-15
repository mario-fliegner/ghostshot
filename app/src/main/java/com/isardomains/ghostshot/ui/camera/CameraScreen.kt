package com.isardomains.ghostshot.ui.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Main camera screen composable.
 *
 * This is a structural placeholder. CameraX preview, overlay rendering,
 * gesture handling, and controls will be added in subsequent implementation steps.
 *
 * @param viewModel The [CameraViewModel] provided by Hilt via the composition.
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Placeholder — replaced when CameraX preview is integrated
        Text(text = "Camera preview placeholder")
    }
}

// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildScreen.kt
package com.isardomains.sameview.ui.wackelbild

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.isardomains.sameview.R
import java.io.File

private const val MAX_PREVIEW_HEIGHT_DP = 500

/**
 * DeinWackelbild entry destination (Block 2 scope).
 *
 * Shows a local, read-only preview of the current session's persisted `reference.jpg`.
 * No sensor/swipe/date/HQ/network behavior exists yet — those are added in later blocks.
 *
 * @param viewModel Hilt ViewModel; owns [sessionId]/[referenceFile] resolution only.
 * @param windowWidthSizeClass Used for the Expanded (≥ 840 dp) max-width constraint.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun WackelbildScreen(
    onBack: () -> Unit,
    viewModel: WackelbildViewModel = hiltViewModel(),
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    WackelbildScreenContent(
        referenceFile = viewModel.referenceFile,
        onBack = onBack,
        windowWidthSizeClass = windowWidthSizeClass
    )
}

/**
 * Stateless content for [WackelbildScreen], taking the reference [File] directly rather than a
 * Hilt ViewModel. Kept `internal` so instrumentation tests can exercise the exact production UI
 * without needing a Hilt-backed [WackelbildViewModel]/[androidx.lifecycle.SavedStateHandle].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun WackelbildScreenContent(
    referenceFile: File,
    onBack: () -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wackelbild_screen_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("wackelbild_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .testTag("wackelbild_screen_root"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(
                        max = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) 680.dp else Dp.Unspecified
                    )
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WackelbildReferencePreview(referenceFile = referenceFile)
            }
        }
    }
}

/**
 * Local, read-only preview of [referenceFile]. Sized from the image's own intrinsic aspect
 * ratio once successfully decoded — never from session metadata and never from a hardcoded
 * fallback ratio (a missing/undecodable file is a fallback-UI case, not a guessed ratio).
 */
@Composable
private fun WackelbildReferencePreview(referenceFile: File) {
    val context = LocalContext.current
    var loadState by remember(referenceFile) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    // Size.ORIGINAL: this preview decides its own layout size *from* the decoded image, so the
    // request must not wait on Coil's default layout-constraints size resolver (which would only
    // resolve once the Image below is placed — a request that will never arrive, since the Image
    // is only placed after a size is already known).
    val request = remember(referenceFile) {
        ImageRequest.Builder(context)
            .data(referenceFile)
            .size(Size.ORIGINAL)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = request,
        onState = { loadState = it }
    )

    val intrinsicRatio: Float? = (loadState as? AsyncImagePainter.State.Success)?.let { success ->
        val w = success.result.drawable.intrinsicWidth
        val h = success.result.drawable.intrinsicHeight
        if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
    }

    val isError = loadState is AsyncImagePainter.State.Error ||
        (loadState is AsyncImagePainter.State.Success && intrinsicRatio == null)

    if (isError) {
        WackelbildPreviewFallback()
        return
    }

    if (intrinsicRatio == null) {
        // Still loading a local file — near-instant; no dedicated loading UI needed for Block 2.
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wackelbild_reference_preview_container")
    ) {
        val availableW = maxWidth
        val compH = (availableW / intrinsicRatio).coerceAtMost(MAX_PREVIEW_HEIGHT_DP.dp)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(availableW)
                .height(compH),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("wackelbild_reference_image")
            )
        }
    }
}

@Composable
private fun WackelbildPreviewFallback() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MAX_PREVIEW_HEIGHT_DP.dp)
            .testTag("wackelbild_preview_fallback"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.wackelbild_preview_error_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wackelbild_preview_error_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

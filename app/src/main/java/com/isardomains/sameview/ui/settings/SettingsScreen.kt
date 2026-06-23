package com.isardomains.sameview.ui.settings

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isardomains.sameview.R
import androidx.compose.ui.graphics.ColorFilter
import com.isardomains.sameview.branding.BuiltinBrandingSymbol
import com.isardomains.sameview.ui.branding.BrandingPreviewCircle
import com.isardomains.sameview.ui.camera.GridType
import com.isardomains.sameview.ui.theme.SameViewSettingsCardSurface
import java.io.File
import com.isardomains.sameview.ui.theme.SameViewSettingsControlOutline
import com.isardomains.sameview.ui.theme.SameViewSettingsLabelText
import com.isardomains.sameview.ui.theme.SameViewSettingsSecondaryText

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onBack: () -> Unit
) {
    val gridType by viewModel.gridType.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val resetOverlayAfterCapture by viewModel.resetOverlayAfterCapture.collectAsStateWithLifecycle()
    val autoOpenCompareAfterCapture by viewModel.autoOpenCompareAfterCapture.collectAsStateWithLifecycle()
    val recreationGuidance by viewModel.recreationGuidance.collectAsStateWithLifecycle()
    val liveDirectionArrow by viewModel.liveDirectionArrow.collectAsStateWithLifecycle()
    val stripOriginalsMetadata by viewModel.stripOriginalsMetadata.collectAsStateWithLifecycle()
    val hasBranding by viewModel.hasBranding.collectAsStateWithLifecycle()
    val globalBrandingFile by viewModel.globalBrandingFile.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedHint by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val brandingLoadError = stringResource(R.string.settings_branding_load_error)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            results[Manifest.permission.ACCESS_MEDIA_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
        if (!granted) {
            val activity = context as? Activity
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                showPermissionDeniedHint = true
            }
        } else {
            showPermissionDeniedHint = false
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onImageUriSelected(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                SettingsUiEvent.RequestLocationPermission -> showRationaleDialog = true
                SettingsUiEvent.BrandingLoadFailed -> snackbarHostState.showSnackbar(brandingLoadError)
            }
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = {
                showRationaleDialog = false
                viewModel.onLocationPermissionResult(false)
            },
            title = { Text(stringResource(R.string.settings_recreation_guidance_rationale_title)) },
            text = { Text(stringResource(R.string.settings_recreation_guidance_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_MEDIA_LOCATION
                    ))
                }) {
                    Text(stringResource(R.string.settings_recreation_guidance_rationale_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    viewModel.onLocationPermissionResult(false)
                }) {
                    Text(stringResource(R.string.settings_recreation_guidance_rationale_cancel))
                }
            }
        )
    }

    SettingsScreenContent(
        gridType = gridType,
        onGridTypeSelected = viewModel::onGridTypeSelected,
        keepScreenOn = keepScreenOn,
        onKeepScreenOnChanged = viewModel::onKeepScreenOnChanged,
        resetOverlayAfterCapture = resetOverlayAfterCapture,
        onResetOverlayAfterCaptureChanged = viewModel::onResetOverlayAfterCaptureChanged,
        autoOpenCompareAfterCapture = autoOpenCompareAfterCapture,
        onAutoOpenCompareAfterCaptureChanged = viewModel::onAutoOpenCompareAfterCaptureChanged,
        recreationGuidance = recreationGuidance,
        onRecreationGuidanceChanged = viewModel::onRecreationGuidanceChanged,
        liveDirectionArrow = liveDirectionArrow,
        onLiveDirectionArrowChanged = viewModel::onLiveDirectionArrowChanged,
        showLocationPermissionDeniedHint = showPermissionDeniedHint,
        stripOriginalsMetadata = stripOriginalsMetadata,
        onStripOriginalsMetadataChanged = viewModel::onStripOriginalsMetadataChanged,
        hasBranding = hasBranding,
        globalBrandingFile = globalBrandingFile,
        onChooseImage = {
            imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onChooseSymbol = viewModel::onSetBrandingFromSymbol,
        onRemoveBranding = viewModel::onRemoveBranding,
        snackbarHostState = snackbarHostState,
        windowWidthSizeClass = windowWidthSizeClass,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun SettingsScreenContent(
    gridType: GridType,
    onGridTypeSelected: (GridType) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    resetOverlayAfterCapture: Boolean,
    onResetOverlayAfterCaptureChanged: (Boolean) -> Unit,
    autoOpenCompareAfterCapture: Boolean,
    onAutoOpenCompareAfterCaptureChanged: (Boolean) -> Unit,
    recreationGuidance: Boolean,
    onRecreationGuidanceChanged: (Boolean) -> Unit,
    liveDirectionArrow: Boolean,
    onLiveDirectionArrowChanged: (Boolean) -> Unit,
    showLocationPermissionDeniedHint: Boolean = false,
    stripOriginalsMetadata: Boolean = false,
    onStripOriginalsMetadataChanged: (Boolean) -> Unit = {},
    hasBranding: Boolean = false,
    globalBrandingFile: File? = null,
    onChooseImage: () -> Unit = {},
    onChooseSymbol: (BuiltinBrandingSymbol) -> Unit = {},
    onRemoveBranding: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onBack: () -> Unit
) {
    var showSymbolDialog by remember { mutableStateOf(false) }

    if (showSymbolDialog) {
        BuiltinSymbolPickerDialog(
            onSymbolSelected = { symbol ->
                showSymbolDialog = false
                onChooseSymbol(symbol)
            },
            onDismiss = { showSymbolDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
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
            SettingsCard(title = stringResource(R.string.settings_camera_title)) {
                KeepScreenOnRow(
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOnChanged
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.settings_grid_type_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = SameViewSettingsSecondaryText
                )
                Spacer(modifier = Modifier.height(8.dp))
                val gridTypes = listOf(GridType.NONE, GridType.RULE_OF_THIRDS, GridType.QUARTERS)
                val gridItems = listOf(
                    SameViewSegmentItem(
                        label = stringResource(R.string.settings_grid_type_none_short),
                        testTag = "settings_grid_type_none"
                    ),
                    SameViewSegmentItem(
                        label = stringResource(R.string.settings_grid_type_rule_of_thirds_short),
                        testTag = "settings_grid_type_rule_of_thirds"
                    ),
                    SameViewSegmentItem(
                        label = stringResource(R.string.settings_grid_type_quarters_short),
                        testTag = "settings_grid_type_quarters"
                    )
                )
                SameViewSegmentControl(
                    items = gridItems,
                    selectedIndex = gridTypes.indexOf(gridType),
                    onItemSelected = { index -> onGridTypeSelected(gridTypes[index]) }
                )
            }
            SettingsCard(title = stringResource(R.string.settings_overlay_compare_title)) {
                ResetOverlayAfterCaptureRow(
                    checked = resetOverlayAfterCapture,
                    onCheckedChange = onResetOverlayAfterCaptureChanged
                )
                AutoOpenCompareAfterCaptureRow(
                    checked = autoOpenCompareAfterCapture,
                    onCheckedChange = onAutoOpenCompareAfterCaptureChanged
                )
            }
            SettingsCard(title = stringResource(R.string.settings_gps_guidance_title)) {
                RecreationGuidanceRow(
                    checked = recreationGuidance,
                    onCheckedChange = onRecreationGuidanceChanged
                )
                if (showLocationPermissionDeniedHint) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_recreation_guidance_permission_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = SameViewSettingsSecondaryText,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                LiveDirectionArrowRow(
                    checked = liveDirectionArrow,
                    enabled = recreationGuidance,
                    onCheckedChange = onLiveDirectionArrowChanged
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_live_direction_arrow_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = SameViewSettingsSecondaryText,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            SettingsCard(title = stringResource(R.string.settings_privacy_title)) {
                StripOriginalsMetadataRow(
                    checked = stripOriginalsMetadata,
                    onCheckedChange = onStripOriginalsMetadataChanged
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_strip_originals_metadata_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = SameViewSettingsSecondaryText,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            SettingsCard(title = stringResource(R.string.settings_branding_section_title)) {
                // Preview circle — visible only when branding is set.
                if (globalBrandingFile != null) {
                    BrandingPreviewCircle(
                        brandingFile = globalBrandingFile,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .testTag("settings_branding_preview")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.settings_branding_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = SameViewSettingsSecondaryText,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onChooseImage,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_branding_choose_image")
                    ) {
                        Text(stringResource(R.string.settings_branding_choose_image))
                    }
                    TextButton(
                        onClick = { showSymbolDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_branding_choose_symbol")
                    ) {
                        Text(stringResource(R.string.settings_branding_choose_symbol))
                    }
                }
                if (hasBranding) {
                    TextButton(
                        onClick = onRemoveBranding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_branding_remove")
                    ) {
                        Text(stringResource(R.string.settings_branding_remove))
                    }
                }
            }
            } // inner Column
        } // outer Column
    }
}

@Composable
private fun KeepScreenOnRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsSwitchRow(
        label = stringResource(R.string.settings_keep_screen_on),
        checked = checked,
        onCheckedChange = onCheckedChange,
        testTag = "settings_keep_screen_on"
    )
}

@Composable
private fun ResetOverlayAfterCaptureRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsSwitchRow(
        label = stringResource(R.string.settings_reset_overlay_after_capture),
        checked = checked,
        onCheckedChange = onCheckedChange,
        testTag = "settings_reset_overlay_after_capture"
    )
}

@Composable
private fun AutoOpenCompareAfterCaptureRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsSwitchRow(
        label = stringResource(R.string.settings_auto_open_compare_after_capture),
        checked = checked,
        onCheckedChange = onCheckedChange,
        testTag = "settings_auto_open_compare_after_capture"
    )
}

@Composable
private fun RecreationGuidanceRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsSwitchRow(
        label = stringResource(R.string.settings_recreation_guidance),
        checked = checked,
        onCheckedChange = onCheckedChange,
        testTag = "settings_recreation_guidance"
    )
}

@Composable
private fun LiveDirectionArrowRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsSwitchRow(
        label = stringResource(R.string.settings_live_direction_arrow),
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        testTag = "settings_live_direction_arrow"
    )
}

@Composable
private fun StripOriginalsMetadataRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsSwitchRow(
        label = stringResource(R.string.settings_strip_originals_metadata_title),
        checked = checked,
        onCheckedChange = onCheckedChange,
        testTag = "settings_strip_originals_metadata"
    )
}

/**
 * AlertDialog that presents the 6 built-in branding symbols in a 2-column grid.
 * Tapping a symbol calls [onSymbolSelected] and dismisses the dialog.
 *
 * Marked `internal` so it can be reused from [EditSessionScreen] without duplication.
 */
@Composable
internal fun BuiltinSymbolPickerDialog(
    onSymbolSelected: (BuiltinBrandingSymbol) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_branding_builtin_symbols_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                items(BuiltinBrandingSymbol.entries) { symbol ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SameViewSettingsCardSurface)
                            .border(1.dp, SameViewSettingsControlOutline, RoundedCornerShape(8.dp))
                            .clickable { onSymbolSelected(symbol) }
                            .padding(12.dp)
                            .testTag("settings_branding_symbol_${symbol.id}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(symbol.drawableRes),
                            contentDescription = symbol.id,
                            colorFilter = ColorFilter.tint(SameViewSettingsLabelText),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = symbol.id.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = SameViewSettingsLabelText
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}


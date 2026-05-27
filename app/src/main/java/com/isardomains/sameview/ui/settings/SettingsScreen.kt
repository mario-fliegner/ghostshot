package com.isardomains.sameview.ui.settings

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.camera.GridType
import com.isardomains.sameview.ui.theme.SameViewSettingsCardSurface
import com.isardomains.sameview.ui.theme.SameViewSettingsControlOutline
import com.isardomains.sameview.ui.theme.SameViewSettingsControlSurface
import com.isardomains.sameview.ui.theme.SameViewSettingsHeaderText
import com.isardomains.sameview.ui.theme.SameViewSettingsLabelText
import com.isardomains.sameview.ui.theme.SameViewSettingsSecondaryText
import com.isardomains.sameview.ui.theme.SameViewSettingsSelectedSegment
import com.isardomains.sameview.ui.theme.SameViewSettingsUnselectedSegment

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val gridType by viewModel.gridType.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val resetOverlayAfterCapture by viewModel.resetOverlayAfterCapture.collectAsStateWithLifecycle()
    val autoOpenCompareAfterCapture by viewModel.autoOpenCompareAfterCapture.collectAsStateWithLifecycle()
    val recreationGuidance by viewModel.recreationGuidance.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedHint by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
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

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                SettingsUiEvent.RequestLocationPermission -> showRationaleDialog = true
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
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
        showLocationPermissionDeniedHint = showPermissionDeniedHint,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    showLocationPermissionDeniedHint: Boolean = false,
    onBack: () -> Unit
) {
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                GridTypeSegmentedControl(
                    selectedGridType = gridType,
                    onGridTypeSelected = onGridTypeSelected
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
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SameViewSettingsCardSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SameViewSettingsHeaderText
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag(testTag)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = SameViewSettingsLabelText,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
private fun GridTypeSegmentedControl(
    selectedGridType: GridType,
    onGridTypeSelected: (GridType) -> Unit
) {
    val items = listOf(
        GridTypeSegment(
            type = GridType.NONE,
            label = stringResource(R.string.settings_grid_type_none_short),
            testTag = "settings_grid_type_none"
        ),
        GridTypeSegment(
            type = GridType.RULE_OF_THIRDS,
            label = stringResource(R.string.settings_grid_type_rule_of_thirds_short),
            testTag = "settings_grid_type_rule_of_thirds"
        ),
        GridTypeSegment(
            type = GridType.QUARTERS,
            label = stringResource(R.string.settings_grid_type_quarters_short),
            testTag = "settings_grid_type_quarters"
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SameViewSettingsControlSurface)
            .border(1.dp, SameViewSettingsControlOutline, MaterialTheme.shapes.medium)
            .padding(3.dp)
            .selectableGroup()
    ) {
        items.forEach { item ->
            GridTypeSegmentButton(
                label = item.label,
                selected = selectedGridType == item.type,
                onClick = { onGridTypeSelected(item.type) },
                testTag = item.testTag,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GridTypeSegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) SameViewSettingsSelectedSegment else SameViewSettingsUnselectedSegment
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .testTag(testTag)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) SameViewSettingsLabelText else SameViewSettingsSecondaryText
        )
    }
}

private data class GridTypeSegment(
    val type: GridType,
    val label: String,
    val testTag: String
)

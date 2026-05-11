package com.isardomains.ghostshot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.camera.GridType

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val gridType by viewModel.gridType.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val resetOverlayAfterCapture by viewModel.resetOverlayAfterCapture.collectAsStateWithLifecycle()
    val autoOpenCompareAfterCapture by viewModel.autoOpenCompareAfterCapture.collectAsStateWithLifecycle()
    SettingsScreenContent(
        gridType = gridType,
        onGridTypeSelected = viewModel::onGridTypeSelected,
        keepScreenOn = keepScreenOn,
        onKeepScreenOnChanged = viewModel::onKeepScreenOnChanged,
        resetOverlayAfterCapture = resetOverlayAfterCapture,
        onResetOverlayAfterCaptureChanged = viewModel::onResetOverlayAfterCaptureChanged,
        autoOpenCompareAfterCapture = autoOpenCompareAfterCapture,
        onAutoOpenCompareAfterCaptureChanged = viewModel::onAutoOpenCompareAfterCaptureChanged,
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
        Column(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = stringResource(R.string.settings_camera_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            KeepScreenOnRow(
                checked = keepScreenOn,
                onCheckedChange = onKeepScreenOnChanged
            )
            Text(
                text = stringResource(R.string.settings_grid_type_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            GridTypeRow(
                label = stringResource(R.string.settings_grid_type_none),
                selected = gridType == GridType.NONE,
                onClick = { onGridTypeSelected(GridType.NONE) },
                testTag = "settings_grid_type_none"
            )
            GridTypeRow(
                label = stringResource(R.string.settings_grid_type_rule_of_thirds),
                selected = gridType == GridType.RULE_OF_THIRDS,
                onClick = { onGridTypeSelected(GridType.RULE_OF_THIRDS) },
                testTag = "settings_grid_type_rule_of_thirds"
            )
            GridTypeRow(
                label = stringResource(R.string.settings_grid_type_quarters),
                selected = gridType == GridType.QUARTERS,
                onClick = { onGridTypeSelected(GridType.QUARTERS) },
                testTag = "settings_grid_type_quarters"
            )
            Text(
                text = stringResource(R.string.settings_overlay_compare_title),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ResetOverlayAfterCaptureRow(
                checked = resetOverlayAfterCapture,
                onCheckedChange = onResetOverlayAfterCaptureChanged
            )
            AutoOpenCompareAfterCaptureRow(
                checked = autoOpenCompareAfterCapture,
                onCheckedChange = onAutoOpenCompareAfterCaptureChanged
            )
        }
    }
}

@Composable
private fun KeepScreenOnRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag("settings_keep_screen_on")
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_keep_screen_on),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ResetOverlayAfterCaptureRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag("settings_reset_overlay_after_capture")
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_reset_overlay_after_capture),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AutoOpenCompareAfterCaptureRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag("settings_auto_open_compare_after_capture")
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_auto_open_compare_after_capture),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun GridTypeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

package com.isardomains.sameview.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.ui.theme.SameViewSettingsCardSurface
import com.isardomains.sameview.ui.theme.SameViewSettingsControlOutline
import com.isardomains.sameview.ui.theme.SameViewSettingsControlSurface
import com.isardomains.sameview.ui.theme.SameViewSettingsHeaderText
import com.isardomains.sameview.ui.theme.SameViewSettingsLabelText
import com.isardomains.sameview.ui.theme.SameViewSettingsSecondaryText
import com.isardomains.sameview.ui.theme.SameViewSettingsSelectedSegment
import com.isardomains.sameview.ui.theme.SameViewSettingsUnselectedSegment

/** Groups related controls in a card surface with an optional section title. */
@Composable
fun SettingsCard(
    title: String? = null,
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
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SameViewSettingsHeaderText
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            content()
        }
    }
}

/** A row with a label on the left and a Switch on the right. The entire row is tappable. */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 8.dp)
    Row(
        modifier = if (testTag != null) rowModifier.testTag(testTag) else rowModifier,
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

/** A single selectable item in a [SameViewSegmentControl]. */
data class SameViewSegmentItem(
    val label: String,
    val testTag: String? = null
)

/**
 * An exclusive-selection segment control matching the SameView design language.
 *
 * Renders a rounded pill container with an accent-tinted selected segment.
 * Each item uses [Role.RadioButton] semantics for accessibility and testability.
 */
@Composable
fun SameViewSegmentControl(
    items: List<SameViewSegmentItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SameViewSettingsControlSurface)
            .border(1.dp, SameViewSettingsControlOutline, MaterialTheme.shapes.medium)
            .padding(3.dp)
            .selectableGroup()
    ) {
        items.forEachIndexed { index, item ->
            val selected = selectedIndex == index
            var itemModifier: Modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (selected) SameViewSettingsSelectedSegment
                    else SameViewSettingsUnselectedSegment
                )
                .selectable(
                    selected = selected,
                    onClick = { onItemSelected(index) },
                    role = Role.RadioButton
                )
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp)
            if (item.testTag != null) {
                itemModifier = itemModifier.testTag(item.testTag)
            }
            Box(modifier = itemModifier, contentAlignment = Alignment.Center) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) SameViewSettingsLabelText else SameViewSettingsSecondaryText
                )
            }
        }
    }
}

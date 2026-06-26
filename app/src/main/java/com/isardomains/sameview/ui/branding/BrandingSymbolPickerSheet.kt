// path: app/src/main/java/com/isardomains/sameview/ui/branding/BrandingSymbolPickerSheet.kt
package com.isardomains.sameview.ui.branding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.R
import com.isardomains.sameview.branding.BuiltinBrandingSymbol
import com.isardomains.sameview.ui.theme.SameViewAccent
import com.isardomains.sameview.ui.theme.SameViewSettingsLabelText

/**
 * ModalBottomSheet that presents the 6 built-in branding symbols as handle-preview cells.
 *
 * Each cell renders the symbol inside a 56 dp circle whose ring geometry matches the
 * comparison handle ring in [ShareComparisonPreview] and [BrandingHandleRenderer]:
 * two 156° [SameViewAccent] arcs with 12° gaps, 2 dp stroke, 1 dp gap to the fill circle.
 *
 * Tapping a cell calls [onSymbolSelected] with the chosen symbol.
 * Tapping Cancel or dismissing the sheet calls [onDismiss].
 *
 * Used from [com.isardomains.sameview.ui.settings.SettingsScreen] (global logo)
 * and [com.isardomains.sameview.ui.compare.ShareComparisonScreen] (session logo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandingSymbolPickerSheet(
    onSymbolSelected: (BuiltinBrandingSymbol) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.branding_symbol_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            val rows = BuiltinBrandingSymbol.entries.chunked(3)
            rows.forEachIndexed { index, rowSymbols ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowSymbols.forEach { symbol ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSymbolSelected(symbol) }
                                .padding(vertical = 8.dp)
                                .testTag("symbol_cell_${symbol.id}")
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Fill circle with symbol icon.
                                // 50 dp = 56 dp total − 2 × (1 dp gap + 1 dp stroke half).
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF5F7FA)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(symbol.drawableRes),
                                        contentDescription = symbol.id,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                // Ring drawn on top: two arcs, 12° gaps at top/bottom.
                                // Same geometry as BrandingPreviewCircle and ShareComparisonPreview.kt.
                                Canvas(modifier = Modifier.size(56.dp)) {
                                    val strokePx = 2.dp.toPx()
                                    val inset = strokePx / 2f
                                    val arcTopLeft = Offset(inset, inset)
                                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                                    drawArc(SameViewAccent, 102f, 156f, false, arcTopLeft, arcSize, style = Stroke(strokePx))
                                    drawArc(SameViewAccent, 282f, 156f, false, arcTopLeft, arcSize, style = Stroke(strokePx))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = symbol.id.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = SameViewSettingsLabelText
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}

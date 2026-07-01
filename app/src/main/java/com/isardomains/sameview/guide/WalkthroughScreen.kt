package com.isardomains.sameview.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.R

@Composable
fun WalkthroughScreen(
    entryMode: WalkthroughEntryMode,
    windowWidthSizeClass: WindowWidthSizeClass,
    onSkip: () -> Unit,
    onStart: () -> Unit
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val pages = WalkthroughContent.pages
    val page = pages[pageIndex]
    val isLastPage = pageIndex == pages.lastIndex

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("walkthrough_screen_root"),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            val useTwoColumns = windowWidthSizeClass != WindowWidthSizeClass.Compact || maxWidth > maxHeight
            val maxContentWidth = if (useTwoColumns) 880.dp else 420.dp
            Column(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .testTag("walkthrough_content"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("walkthrough_brand")
                )
                if (useTwoColumns) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("walkthrough_two_column_layout"),
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WalkthroughMockup(
                            pageId = page.id,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(0.72f)
                        )
                        WalkthroughTextAndControls(
                            page = page,
                            pageIndex = pageIndex,
                            pageCount = pages.size,
                            isLastPage = isLastPage,
                            onSkip = onSkip,
                            onBack = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                            onNext = { pageIndex = (pageIndex + 1).coerceAtMost(pages.lastIndex) },
                            onStart = onStart,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.testTag("walkthrough_single_column_layout"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        WalkthroughMockup(
                            pageId = page.id,
                            modifier = Modifier.fillMaxWidth()
                        )
                        WalkthroughTextAndControls(
                            page = page,
                            pageIndex = pageIndex,
                            pageCount = pages.size,
                            isLastPage = isLastPage,
                            onSkip = onSkip,
                            onBack = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                            onNext = { pageIndex = (pageIndex + 1).coerceAtMost(pages.lastIndex) },
                            onStart = onStart,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkthroughTextAndControls(
    page: WalkthroughPage,
    pageIndex: Int,
    pageCount: Int,
    isLastPage: Boolean,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("walkthrough_title")
        )
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("walkthrough_body")
        )
        WalkthroughProgressDots(
            pageIndex = pageIndex,
            pageCount = pageCount
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLastPage) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("walkthrough_back")
                ) {
                    Text(stringResource(R.string.walkthrough_back))
                }
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("walkthrough_start")
                ) {
                    Text(stringResource(R.string.walkthrough_start))
                }
            } else {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("walkthrough_skip")
                ) {
                    Text(stringResource(R.string.walkthrough_skip))
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("walkthrough_next")
                ) {
                    Text(stringResource(R.string.walkthrough_next))
                }
            }
        }
    }
}

@Composable
private fun WalkthroughProgressDots(
    pageIndex: Int,
    pageCount: Int
) {
    Row(
        modifier = Modifier.testTag("walkthrough_progress_dots"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == pageIndex) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == pageIndex) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                    .testTag("walkthrough_progress_dot_$index")
            )
        }
    }
}

@Composable
private fun WalkthroughMockup(
    pageId: WalkthroughPageId,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .aspectRatio(0.78f)
            .testTag("walkthrough_mockup_${pageId.name.lowercase()}"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            when (pageId) {
                WalkthroughPageId.CHOOSE_PHOTO -> ChoosePhotoMockup()
                WalkthroughPageId.ALIGN_OVERLAY -> AlignOverlayMockup()
                WalkthroughPageId.CAPTURE -> CaptureMockup()
                WalkthroughPageId.COMPARE -> CompareMockup()
            }
        }
    }
}

@Composable
private fun ChoosePhotoMockup() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MockPhotoCard(Modifier.weight(1f).fillMaxWidth(), MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                )
            }
        }
    }
}

@Composable
private fun AlignOverlayMockup() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .testTag("walkthrough_align_overlay_mockup")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.58f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .testTag("walkthrough_reference_overlay")
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun CaptureMockup() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        MockPhotoCard(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.7f)
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .size(58.dp)
                .clip(CircleShape)
                .border(5.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

@Composable
private fun CompareMockup() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(4.dp)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun MockPhotoCard(
    modifier: Modifier,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    )
}

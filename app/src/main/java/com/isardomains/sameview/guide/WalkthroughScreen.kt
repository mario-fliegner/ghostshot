package com.isardomains.sameview.guide

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.isardomains.sameview.R
import kotlinx.coroutines.launch

@Composable
fun WalkthroughScreen(
    entryMode: WalkthroughEntryMode,
    windowWidthSizeClass: WindowWidthSizeClass,
    onSkip: () -> Unit,
    onStart: () -> Unit
) {
    val pages = WalkthroughContent.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val useTwoColumns = windowWidthSizeClass != WindowWidthSizeClass.Compact

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("walkthrough_screen_root"),
        color = MaterialTheme.colorScheme.background
    ) {
        // safeDrawingPadding keeps content clear of status bars, navigation bars, and display
        // cutouts in both portrait and landscape, including reverse-landscape on 3-button nav.
        // .scrollable() is placed before .padding() so its pointer-input region covers the full
        // safe-drawing area, including the 24dp/16dp layout padding gutter — the entire visible
        // page is swipeable, with only real system areas outside safeDrawingPadding() excluded.
        // .padding() still applies to the Box's children below (contentAlignment = Center), so
        // visual positions and the 24dp/16dp inset are unchanged.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .scrollable(
                    orientation = Orientation.Horizontal,
                    state = pagerState,
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
                    reverseDirection = true
                )
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Text and controls are outside the pager so only one instance is ever composed at a
            // time, regardless of HorizontalPager adjacent-page pre-composition.
            val currentPageIndex = pagerState.currentPage
            val currentPage = pages[currentPageIndex]
            val isLastPage = currentPageIndex == pages.lastIndex
            val onBack: () -> Unit = {
                scope.launch { pagerState.animateScrollToPage(currentPageIndex - 1) }
            }
            val onNext: () -> Unit = {
                scope.launch { pagerState.animateScrollToPage(currentPageIndex + 1) }
            }

            val maxContentWidth = if (useTwoColumns) 880.dp else 420.dp
            Column(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .testTag("walkthrough_content"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (useTwoColumns) {
                    // Landscape / tablet: image left, slot-based content right.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("walkthrough_two_column_layout"),
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: image slot.
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(0.72f)
                                .testTag("walkthrough_pager")
                        ) { pageIndex ->
                            WalkthroughMockup(
                                pageId = pages[pageIndex].id,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Right: text area (measured max height) + dots + buttons. The text
                        // area no longer expands via weight, so the group is centered as a
                        // fixed-height block instead — this keeps it symmetric regardless of
                        // which page's content is currently shown.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            WalkthroughTextArea(
                                currentPage = currentPage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("walkthrough_text_area"),
                                contentAlignment = Alignment.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            WalkthroughProgressDots(
                                pageIndex = currentPageIndex,
                                pageCount = pages.size
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            WalkthroughButtons(
                                pageIndex = currentPageIndex,
                                isLastPage = isLastPage,
                                onSkip = onSkip,
                                onBack = onBack,
                                onNext = onNext,
                                onStart = onStart
                            )
                        }
                    }
                } else {
                    // Portrait: single-column, slot-based layout. No Arrangement.Center on this
                    // column — items flow top-to-bottom so each slot occupies a stable position.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("walkthrough_single_column_layout"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Leading slack: the only weighted child left in this Column, so it
                        // absorbs whatever height remains after the image, the measured text
                        // area, and the fixed dots/buttons/spacers. Its value is identical on
                        // every page since the text area height below it no longer varies by
                        // page — this is what keeps dots/buttons pinned in place while the image
                        // moves by however much the reserved text height required.
                        Spacer(modifier = Modifier.weight(1f))

                        // Image slot: height fixed by aspect ratio — position never changes.
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth(0.74f)
                                .aspectRatio(3f / 4f)
                                .testTag("walkthrough_pager")
                        ) { pageIndex ->
                            WalkthroughMockup(
                                pageId = pages[pageIndex].id,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Text area: height is the measured maximum across all four pages (see
                        // WalkthroughTextArea), not a weight-based leftover, so the slot fits the
                        // tallest page instead of clipping it. Top-aligned so the title appears
                        // immediately below the image; empty space accumulates below the body
                        // text on shorter pages.
                        WalkthroughTextArea(
                            currentPage = currentPage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("walkthrough_text_area"),
                            contentAlignment = Alignment.TopCenter
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Dots slot: fixed position — never moves regardless of text length.
                        WalkthroughProgressDots(
                            pageIndex = currentPageIndex,
                            pageCount = pages.size
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Button slot: fixed position — never moves regardless of text length.
                        WalkthroughButtons(
                            pageIndex = currentPageIndex,
                            isLastPage = isLastPage,
                            onSkip = onSkip,
                            onBack = onBack,
                            onNext = onNext,
                            onStart = onStart
                        )

                        // Portrait-only extra bottom breathing room, separate from the shared
                        // Box padding (which also applies to landscape).
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// Reserves the same text height on every page, measured from all of WalkthroughContent.pages
// rather than a weight-based leftover split. The measured-height computation never reads
// currentPage, so it depends only on width/locale/density/font-scale — not on page swipes.
@Composable
private fun WalkthroughTextArea(
    currentPage: WalkthroughPage,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val measureConstraints = Constraints(
            minWidth = constraints.maxWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
            maxHeight = Constraints.Infinity
        )

        val maxTextHeightPx = subcompose("walkthrough_text_measure") {
            WalkthroughContent.pages.forEach { page ->
                WalkthroughTextSlot(page = page, isMeasurement = true)
            }
        }.maxOf { measurable -> measurable.measure(measureConstraints).height }

        val visiblePlaceables = subcompose("walkthrough_text_visible") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = contentAlignment
            ) {
                WalkthroughTextSlot(page = currentPage)
            }
        }.map { measurable ->
            measurable.measure(
                Constraints(
                    minWidth = constraints.maxWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = maxTextHeightPx,
                    maxHeight = maxTextHeightPx
                )
            )
        }

        layout(constraints.maxWidth, maxTextHeightPx) {
            visiblePlaceables.forEach { it.place(0, 0) }
        }
    }
}

@Composable
private fun WalkthroughTextSlot(page: WalkthroughPage, isMeasurement: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isMeasurement) Modifier.clearAndSetSemantics {} else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = if (isMeasurement) Modifier else Modifier.testTag("walkthrough_title")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = if (isMeasurement) Modifier else Modifier.testTag("walkthrough_body")
        )
    }
}

@Composable
private fun WalkthroughButtons(
    pageIndex: Int,
    isLastPage: Boolean,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit
) {
    when {
        isLastPage -> {
            // Page 4: Back (medium) + Start (high)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("walkthrough_button_row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
            }
        }
        pageIndex == 0 -> {
            // Page 1: Skip (low) + Next (high)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("walkthrough_button_row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
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
        else -> {
            // Pages 2–3: Back (medium) + Next (high)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("walkthrough_button_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("walkthrough_back")
                ) {
                    Text(stringResource(R.string.walkthrough_back))
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
private fun WalkthroughProgressDots(pageIndex: Int, pageCount: Int) {
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
        modifier = modifier.testTag("walkthrough_mockup_${pageId.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (pageId) {
                WalkthroughPageId.THEN_AND_NOW -> ThenAndNowMockup()
                WalkthroughPageId.ALIGN_OVERLAY -> AlignOverlayMockup()
                WalkthroughPageId.TAKE_SHOT -> TakeShotMockup()
                WalkthroughPageId.SEE_WHAT_CHANGED -> SeeWhatChangedMockup()
            }
        }
    }
}

// ── ThenAndNowMockup — page 1, WEBP artwork ──────────────────────────────────

@Composable
private fun ThenAndNowMockup() {
    Image(
        painter = painterResource(R.drawable.walkthrough_step1),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .testTag("walkthrough_step1_image")
    )
}

// ── AlignOverlayMockup — page 2, WEBP artwork ────────────────────────────────

@Composable
private fun AlignOverlayMockup() {
    Image(
        painter = painterResource(R.drawable.walkthrough_step2),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .testTag("walkthrough_step2_image")
    )
}

// ── TakeShotMockup — page 3, WEBP artwork ────────────────────────────────────

@Composable
private fun TakeShotMockup() {
    Image(
        painter = painterResource(R.drawable.walkthrough_step3),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .testTag("walkthrough_step3_image")
    )
}

// ── SeeWhatChangedMockup — page 4, WEBP artwork ──────────────────────────────

@Composable
private fun SeeWhatChangedMockup() {
    Image(
        painter = painterResource(R.drawable.walkthrough_step4),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .testTag("walkthrough_step4_image")
    )
}

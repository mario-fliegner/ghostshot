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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
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

                        // Right: text slot (weight) + dots + buttons.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .scrollable(
                                    orientation = Orientation.Horizontal,
                                    state = pagerState,
                                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
                                    reverseDirection = true
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Text slot: weight(1f) is the only region that absorbs
                            // content-length variation; its boundaries never move.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                WalkthroughTextSlot(page = currentPage)
                            }

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

                        // Text slot: top-aligned so the title appears immediately below the
                        // image. Empty space accumulates below the body text, not between the
                        // image and title. Slot boundaries are fixed; only content length varies.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .scrollable(
                                    orientation = Orientation.Horizontal,
                                    state = pagerState,
                                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
                                    reverseDirection = true
                                ),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            WalkthroughTextSlot(page = currentPage)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Dots slot: fixed position — never moves regardless of text length.
                        WalkthroughProgressDots(
                            pageIndex = currentPageIndex,
                            pageCount = pages.size
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Button slot: fixed position — never moves regardless of text length.
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
            }
        }
    }
}

@Composable
private fun WalkthroughTextSlot(page: WalkthroughPage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("walkthrough_title")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("walkthrough_body")
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
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
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
            // Pages 2–3: Skip (low) + Back (medium) + Next (high)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("walkthrough_skip")
                ) {
                    Text(stringResource(R.string.walkthrough_skip))
                }
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

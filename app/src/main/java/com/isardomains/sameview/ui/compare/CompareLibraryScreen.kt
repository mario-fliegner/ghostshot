package com.isardomains.sameview.ui.compare

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.isardomains.sameview.R
import com.isardomains.sameview.guide.GuideTip
import com.isardomains.sameview.guide.GuideTipController
import com.isardomains.sameview.guide.GuideTipDismissReason
import com.isardomains.sameview.guide.GuideTipEvaluationContext
import com.isardomains.sameview.guide.GuideTipId
import com.isardomains.sameview.guide.GuideTipRegistry
import com.isardomains.sameview.guide.GuideTipScope
import com.isardomains.sameview.guide.GuideTopicId
import com.isardomains.sameview.ui.camera.ScannedSession
import com.isardomains.sameview.ui.settings.LibraryFilter
import com.isardomains.sameview.ui.settings.LibrarySortOrder
import com.isardomains.sameview.ui.theme.SameViewAccent
import com.isardomains.sameview.ui.theme.SameViewAppSurface
import com.isardomains.sameview.ui.theme.SameViewStarFavorited
import com.isardomains.sameview.ui.theme.SameViewAppSurfaceElevated
import com.isardomains.sameview.ui.theme.SameViewSelectionOverlay
import com.isardomains.sameview.ui.theme.SameViewTextPrimary
import com.isardomains.sameview.ui.theme.SameViewTextSecondary
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CompareLibraryScreen(
    sessions: List<ScannedSession>,
    onRefresh: () -> Unit,
    onSessionClick: (ScannedSession) -> Unit,
    onBack: () -> Unit,
    onDeleteSessions: (List<String>) -> Unit = {},
    onBackupSessions: (sessionIds: List<String>, destinationUri: Uri) -> Unit = { _, _ -> },
    onToggleFavorite: (sessionId: String) -> Unit = {},
    libraryFilter: LibraryFilter = LibraryFilter.ALL,
    librarySortOrder: LibrarySortOrder = LibrarySortOrder.NEWEST_FIRST,
    onSetLibraryFilter: (LibraryFilter) -> Unit = {},
    onSetLibrarySortOrder: (LibrarySortOrder) -> Unit = {},
    isBackupInProgress: Boolean = false,
    isDeletionInProgress: Boolean = false,
    backupSuccessGeneration: Long = 0L,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    guideTipController: GuideTipController? = null,
    onGuideTipLearnMore: ((GuideTopicId) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current
    var selectionMode by remember { mutableStateOf(false) }
    var selectedSessionIds by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSortFilterMenu by remember { mutableStateOf(false) }
    // IDs captured at backup-initiation time, persisted across Activity recreation so the
    // SAF callback always receives the original selection even after process death.
    var pendingBackupSessionIds by rememberSaveable { mutableStateOf(listOf<String>()) }

    val coroutineScope = rememberCoroutineScope()
    val lazyGridState = rememberLazyGridState()

    val openComparisonTipCompleted by remember(guideTipController) {
        guideTipController?.observeTipSeen(GuideTipId.OPEN_COMPARISON) ?: flowOf(false)
    }.collectAsState(initial = false)
    val activeGuideTipId by remember(guideTipController) {
        guideTipController?.activeTipId ?: MutableStateFlow<GuideTipId?>(null)
    }.collectAsState()
    // OPEN_COMPARISON is the only Library tip and always renders as the inline card above
    // the grid (see the grid Column below) — it is never floated via GuideTipHost.
    val activeGuideTip = activeGuideTipId?.let { GuideTipRegistry.tipFor(it) }

    // Filter then sort; cached on any input change.
    val displayedSessions = remember(sessions, libraryFilter, librarySortOrder) {
        val filtered = when (libraryFilter) {
            LibraryFilter.FAVORITES -> sessions.filter { it.isFavorite }
            else -> sessions
        }
        when (librarySortOrder) {
            LibrarySortOrder.OLDEST_FIRST -> filtered.sortedBy { it.timestamp }
            else -> filtered.sortedByDescending { it.timestamp }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onBackupSessions(pendingBackupSessionIds, uri)
    }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    LaunchedEffect(sessions.isEmpty()) {
        if (sessions.isEmpty()) {
            selectionMode = false
            selectedSessionIds = emptySet()
        }
    }

    // Exit selection mode when the displayed list becomes empty (e.g. last favorited session
    // is unfavorited while Favorites-only filter is active).
    LaunchedEffect(displayedSessions.isEmpty()) {
        if (displayedSessions.isEmpty()) {
            selectionMode = false
            selectedSessionIds = emptySet()
        }
    }

    // Exit selection mode after a successful backup (BackupSucceeded event increments this counter).
    LaunchedEffect(backupSuccessGeneration) {
        if (backupSuccessGeneration > 0L) {
            selectionMode = false
            selectedSessionIds = emptySet()
        }
    }

    val isGridScrollInProgress by remember { derivedStateOf { lazyGridState.isScrollInProgress } }
    // A raw scroll blip (e.g. incidental touch settling right as the grid first lays out)
    // must not instantly and irrecoverably clear the tip. Only a scroll that is still in
    // progress after a short debounce counts as a real, deliberate scroll for blocking
    // purposes — momentary blips settle within the debounce window and never set this true.
    var isGridScrollBlockConfirmed by remember { mutableStateOf(false) }
    LaunchedEffect(isGridScrollInProgress) {
        if (isGridScrollInProgress) {
            delay(150)
            isGridScrollBlockConfirmed = true
        } else {
            isGridScrollBlockConfirmed = false
        }
    }
    val libraryEligibleTipIds = remember(displayedSessions, openComparisonTipCompleted) {
        buildSet<GuideTipId> {
            if (displayedSessions.isNotEmpty() && !openComparisonTipCompleted) {
                add(GuideTipId.OPEN_COMPARISON)
            }
        }
    }
    val libraryTipBlocked = selectionMode ||
        showDeleteConfirmDialog ||
        isBackupInProgress ||
        isGridScrollBlockConfirmed ||
        showSortFilterMenu

    // Screen-entry delay is a one-shot, stably-keyed effect, decoupled from eligibility
    // evaluation — otherwise a transient state change (e.g. openComparisonTipCompleted's
    // collectAsState briefly reporting its initial false before the real persisted value
    // arrives) restarts the delay from zero on every such blip, and evaluate() may never
    // actually run before the user navigates away again.
    var isLibraryEntryDelayElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600L)
        isLibraryEntryDelayElapsed = true
    }

    LaunchedEffect(libraryEligibleTipIds, libraryTipBlocked, isLibraryEntryDelayElapsed) {
        val controller = guideTipController ?: return@LaunchedEffect
        if (libraryTipBlocked) {
            // OPEN_COMPARISON is the only Library tip. Scoped so this doesn't wipe out a
            // different screen's active tip in the brief window before this screen's own tip
            // has become active (e.g. a stale value still observed from the previous screen).
            controller.clearActiveTipWithoutMarkingSeen(GuideTipId.OPEN_COMPARISON)
        } else if (isLibraryEntryDelayElapsed) {
            controller.evaluate(
                GuideTipEvaluationContext(
                    scope = GuideTipScope.LIBRARY,
                    eligibleTipIds = libraryEligibleTipIds,
                    isBlockedByTransientUi = false
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // CompareLibraryScreen only ever owns OPEN_COMPARISON. Scoped so a late dispose
            // (Compose Navigation may tear this screen down well after the next screen has
            // already mounted) can't wipe out a different screen's active tip.
            guideTipController?.clearActiveTipWithoutMarkingSeen(GuideTipId.OPEN_COMPARISON)
        }
    }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedSessionIds = emptySet()
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.compare_library_delete_dialog_title)) },
            text = { Text(stringResource(R.string.compare_library_delete_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToDelete = selectedSessionIds.toList()
                        showDeleteConfirmDialog = false
                        selectionMode = false
                        selectedSessionIds = emptySet()
                        onDeleteSessions(idsToDelete)
                    }
                ) {
                    Text(stringResource(R.string.compare_library_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.compare_library_delete_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.testTag("compare_library_screen"),
        topBar = {
            Column {
            if (selectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                R.string.compare_library_selection_count,
                                selectedSessionIds.size
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                selectionMode = false
                                selectedSessionIds = emptySet()
                            },
                            modifier = Modifier.testTag("compare_library_cancel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.compare_library_cancel_selection)
                            )
                        }
                    },
                    actions = {
                        val allDisplayedSelected = displayedSessions.isNotEmpty() &&
                            displayedSessions.all { it.sessionId in selectedSessionIds }
                        IconButton(
                            onClick = {
                                if (allDisplayedSelected) {
                                    selectedSessionIds = emptySet()
                                } else {
                                    selectedSessionIds = displayedSessions.map { it.sessionId }.toSet()
                                }
                            },
                            modifier = Modifier.testTag("compare_library_select_all_toggle")
                        ) {
                            Icon(
                                imageVector = if (allDisplayedSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = stringResource(
                                    if (allDisplayedSelected) R.string.compare_library_deselect_all
                                    else R.string.compare_library_select_all
                                )
                            )
                        }
                        IconButton(
                            onClick = {
                                val ids = selectedSessionIds.toList()
                                pendingBackupSessionIds = ids
                                val suggestedFilename = if (ids.size == 1) {
                                    resources.getString(R.string.session_backup_filename_single, ids[0])
                                } else {
                                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                                        .format(Date())
                                    resources.getString(R.string.session_backup_filename_multi, timestamp)
                                }
                                createDocumentLauncher.launch(suggestedFilename)
                            },
                            enabled = selectedSessionIds.isNotEmpty() && !isBackupInProgress && !isDeletionInProgress,
                            modifier = Modifier.testTag("compare_library_backup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = stringResource(R.string.compare_library_action_backup)
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            enabled = selectedSessionIds.isNotEmpty() && !isBackupInProgress,
                            modifier = Modifier.testTag("compare_library_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.compare_library_delete_selected)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.compare_library_title)) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("compare_library_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.compare_back)
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showSortFilterMenu = true },
                                modifier = Modifier.testTag("compare_library_overflow_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.compare_library_filter_sort_options)
                                )
                            }
                            DropdownMenu(
                                expanded = showSortFilterMenu,
                                onDismissRequest = { showSortFilterMenu = false }
                            ) {
                                // Filter section header — plain Text label, not a disabled menu item
                                Text(
                                    text = stringResource(R.string.compare_library_filter_header),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .fillMaxWidth()
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.compare_library_filter_all)) },
                                    leadingIcon = {
                                        if (libraryFilter == LibraryFilter.ALL) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    },
                                    onClick = { onSetLibraryFilter(LibraryFilter.ALL); showSortFilterMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.compare_library_filter_favorites)) },
                                    leadingIcon = {
                                        if (libraryFilter == LibraryFilter.FAVORITES) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    },
                                    onClick = { onSetLibraryFilter(LibraryFilter.FAVORITES); showSortFilterMenu = false }
                                )
                                HorizontalDivider()
                                // Sort section header — plain Text label, not a disabled menu item
                                Text(
                                    text = stringResource(R.string.compare_library_sort_header),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .fillMaxWidth()
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.compare_library_sort_newest_first)) },
                                    leadingIcon = {
                                        if (librarySortOrder == LibrarySortOrder.NEWEST_FIRST) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    },
                                    onClick = { onSetLibrarySortOrder(LibrarySortOrder.NEWEST_FIRST); showSortFilterMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.compare_library_sort_oldest_first)) },
                                    leadingIcon = {
                                        if (librarySortOrder == LibrarySortOrder.OLDEST_FIRST) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    },
                                    onClick = { onSetLibrarySortOrder(LibrarySortOrder.OLDEST_FIRST); showSortFilterMenu = false }
                                )
                            }
                        }
                    }
                )
            }
            if (isBackupInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compare_library_progress_indicator")
                )
            }
            } // end Column
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .testTag("compare_library_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .testTag("compare_library_empty_card"),
                        shape = MaterialTheme.shapes.medium,
                        color = SameViewAppSurface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SameViewAppSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CompareArrows,
                                    contentDescription = null,
                                    tint = SameViewTextPrimary.copy(alpha = 0.88f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = stringResource(R.string.compare_library_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = SameViewTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.compare_library_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SameViewTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(
                                    contentColor = SameViewTextPrimary
                                ),
                                modifier = Modifier.testTag("compare_library_empty_cta")
                            ) {
                                Text(stringResource(R.string.compare_library_empty_cta))
                            }
                        }
                    }
                }
            } else if (displayedSessions.isEmpty()) {
                // Favorites-only filter active but no sessions are favorited yet
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .testTag("compare_library_empty_favorites_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .testTag("compare_library_empty_favorites_card"),
                        shape = MaterialTheme.shapes.medium,
                        color = SameViewAppSurface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SameViewAppSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = SameViewStarFavorited.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = stringResource(R.string.compare_library_empty_favorites_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = SameViewTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.compare_library_empty_favorites_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SameViewTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                val columnCount = when (windowWidthSizeClass) {
                    WindowWidthSizeClass.Medium -> 3
                    WindowWidthSizeClass.Expanded -> 4
                    else -> 2
                }
                // Grid section wrapped in a Column that applies innerPadding. The inline Open
                // Comparison tip (if active) takes its own row above the grid in normal layout
                // flow, so it can never overlap a tile regardless of session count — the grid
                // only gets the remaining space via weight(1f). OPEN_COMPARISON is the only
                // Library tip and always renders inline here; there is no floating Library tip
                // and therefore no GuideTipHost/anchor plumbing needed for this screen.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (activeGuideTip?.id == GuideTipId.OPEN_COMPARISON) {
                        OpenComparisonInlineTipCard(
                            tip = activeGuideTip,
                            onDismiss = {
                                coroutineScope.launch {
                                    guideTipController?.dismissActiveTip(GuideTipDismissReason.GOT_IT)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Fixed(columnCount),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("compare_library_grid")
                    ) {
                        items(displayedSessions, key = { it.sessionId }) { session ->
                            CompareSessionTile(
                                session = session,
                                isSelected = session.sessionId in selectedSessionIds,
                                isSelectionMode = selectionMode,
                                onToggleFavorite = { onToggleFavorite(session.sessionId) },
                                onClick = {
                                    guideTipController?.onUserAction()
                                    if (selectionMode) {
                                        val newSelection = if (session.sessionId in selectedSessionIds) {
                                            selectedSessionIds - session.sessionId
                                        } else {
                                            selectedSessionIds + session.sessionId
                                        }
                                        selectedSessionIds = newSelection
                                        if (newSelection.isEmpty()) {
                                            selectionMode = false
                                        }
                                    } else {
                                        // OPEN_COMPARISON completes via Dismiss only (see
                                        // GUIDE_TIPS_UX_V1.md §6.2/§7.4/§15.3) — tapping a tile
                                        // does not complete or mark it seen. If it was visible,
                                        // leaving this screen clears it via the existing dispose
                                        // cleanup (clearActiveTipWithoutMarkingSeen()) without
                                        // persisting completion, so it reappears on the next
                                        // Library visit.
                                        onSessionClick(session)
                                    }
                                },
                                onLongClick = {
                                    guideTipController?.onUserAction()
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedSessionIds = setOf(session.sessionId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Inline replacement for the floating GuideTipHost card, used only for OPEN_COMPARISON.
 * Rendered in normal layout flow above the grid so it reserves real space and can never
 * overlap a tile, at any session count. No pointer — the tip refers to the whole grid, not
 * a specific anchor. Visual language (surface color, border, typography, action styling)
 * mirrors GuideTipHost's card so the two tip presentations still feel like one system.
 */
@Composable
private fun OpenComparisonInlineTipCard(
    tip: GuideTip,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("guide_tip_open_comparison_inline_card"),
        colors = CardDefaults.cardColors(containerColor = SameViewAppSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, SameViewAccent)
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 0.dp)
        ) {
            Text(
                text = stringResource(tip.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("guide_tip_open_comparison_inline_title")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(tip.bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = SameViewTextSecondary,
                // 3 lines (not the global GuideTipHost card's 2): this copy is two full
                // sentences — one per Library action (open, multi-select) — and must not be
                // truncated. This only affects the OPEN_COMPARISON inline card, not the
                // shared floating GuideTipHost card used by other screens.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("guide_tip_open_comparison_inline_body")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp, alignment = Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onDismiss, role = Role.Button)
                        .testTag("guide_tip_open_comparison_inline_dismiss"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.guide_tip_dismiss),
                        style = MaterialTheme.typography.labelMedium,
                        color = SameViewAccent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompareSessionTile(
    session: ScannedSession,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timestamp = formatTimestamp(session.timestamp)
    val locationText = formatLibraryLocation(
        session.locationDisplayName,
        session.locationCity,
        session.locationCountry
    )
    val hasTitle = !session.title.isNullOrEmpty()
    val hasLocation = locationText != null

    val metaForDescription = buildString {
        if (hasTitle) append(session.title)
        if (hasTitle && hasLocation) append(", ")
        if (hasLocation) append(locationText)
    }
    val tileDescription = if (metaForDescription.isNotEmpty()) {
        stringResource(
            R.string.compare_library_session_content_description_with_meta,
            metaForDescription,
            timestamp
        )
    } else {
        stringResource(R.string.compare_library_session_content_description, timestamp)
    }

    val selectedDesc = stringResource(R.string.compare_library_session_selected)
    val notSelectedDesc = stringResource(R.string.compare_library_session_not_selected)
    val reservedTextHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp() * 2
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SameViewAppSurface)
            .testTag("compare_library_session_tile_${session.sessionId}")
            .semantics {
                contentDescription = tileDescription
                if (isSelectionMode) {
                    stateDescription = if (isSelected) selectedDesc else notSelectedDesc
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
            ) {
                AsyncImage(
                    model = session.referenceFileUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("compare_library_reference_image_${session.sessionId}")
                )
                AsyncImage(
                    model = session.captureFileUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("compare_library_capture_image_${session.sessionId}")
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Spacer(modifier = Modifier.height(reservedTextHeight))
                when {
                    hasTitle && hasLocation -> {
                        // Fall A: Titel und Location vorhanden — kein Datum
                        Column {
                            Text(
                                text = session.title!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = locationText!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    hasTitle -> {
                        // Fall B: Titel, keine Location — Zeile 1: Titel, Zeile 2: Datum
                        Column {
                            Text(
                                text = session.title!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    hasLocation -> {
                        // Fall C: Location, kein Titel — Zeile 1: Location, Zeile 2: Datum
                        Column {
                            Text(
                                text = locationText!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    else -> {
                        // Fall D: Weder Titel noch Location — nur Datum, zentriert
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(SameViewSelectionOverlay)
            )
        }
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = SameViewAccent,
                    checkmarkColor = SameViewTextPrimary,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
        // Favourite star — hidden in multi-select mode; touches consumed to prevent session-open and multi-select
        if (!isSelectionMode) {
            val starDescription = stringResource(
                if (session.isFavorite) R.string.compare_library_tile_favorite_remove
                else R.string.compare_library_tile_favorite_mark
            )
            // Touch target: 48dp, positioned at tile TopStart corner.
            // Icon and scrim anchor to TopStart of the touch target so the visual star
            // sits near the tile corner (~13–14dp from corner), matching the visual
            // proximity of the Selection Checkbox at TopEnd.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 4.dp)
                    .size(48.dp)
                    .testTag("compare_library_tile_favorite_star_${session.sessionId}")
                    .semantics {
                        role = Role.Button
                        contentDescription = starDescription
                        onClick(label = starDescription, action = {
                            onToggleFavorite()
                            true
                        })
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggleFavorite() },
                            onLongPress = {} // consumed without action to prevent multi-select
                        )
                    },
                contentAlignment = Alignment.TopStart
            ) {
                // 20dp inner Box centers the 18dp icon within the circular scrim.
                // Without this wrapper both elements share the same (0,0) origin inside
                // the 48dp TopStart touch target, shifting the icon 1dp up/left of the
                // circle's visual center.
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(SameViewAppSurface.copy(alpha = 0.30f))
                    )
                    Icon(
                        imageVector = if (session.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (session.isFavorite) SameViewStarFavorited
                               else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Formats user-authored location fields into a single display string following the §32 priority
 * order. Returns null when no location data is present.
 *
 * Uses simple string concatenation without width measurement; TextOverflow.Ellipsis handles
 * truncation at the call site.
 */
private fun formatLibraryLocation(
    displayName: String?,
    city: String?,
    country: String?
): String? {
    if (displayName != null) {
        return when {
            city != null && country != null -> "$displayName · $city, $country"
            city != null -> "$displayName · $city"
            country != null -> "$displayName · $country"
            else -> displayName
        }
    }
    return when {
        city != null && country != null -> "$city, $country"
        city != null -> city
        country != null -> country
        else -> null
    }
}

@Composable
private fun formatTimestamp(timestampMs: Long): String =
    remember(timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(timestampMs))
    }

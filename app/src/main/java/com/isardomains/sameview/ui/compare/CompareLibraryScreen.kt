package com.isardomains.sameview.ui.compare

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.isardomains.sameview.R
import com.isardomains.sameview.ui.camera.ScannedSession
import com.isardomains.sameview.ui.theme.SameViewAccent
import com.isardomains.sameview.ui.theme.SameViewAppSurface
import com.isardomains.sameview.ui.theme.SameViewAppSurfaceElevated
import com.isardomains.sameview.ui.theme.SameViewSelectionOverlay
import com.isardomains.sameview.ui.theme.SameViewTextPrimary
import com.isardomains.sameview.ui.theme.SameViewTextSecondary
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CompareLibraryScreen(
    sessions: List<ScannedSession>,
    onRefresh: () -> Unit,
    onSessionClick: (ScannedSession) -> Unit,
    onBack: () -> Unit,
    onDeleteSessions: (List<String>) -> Unit = {},
    onBackupSessions: (sessionIds: List<String>, destinationUri: Uri) -> Unit = { _, _ -> },
    isBackupInProgress: Boolean = false,
    isDeletionInProgress: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectionMode by remember { mutableStateOf(false) }
    var selectedSessionIds by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onBackupSessions(selectedSessionIds.toList(), uri)
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
                        val allSelected = selectedSessionIds.size == sessions.size
                        IconButton(
                            onClick = {
                                if (allSelected) {
                                    selectedSessionIds = emptySet()
                                } else {
                                    selectedSessionIds = sessions.map { it.sessionId }.toSet()
                                }
                            },
                            modifier = Modifier.testTag("compare_library_select_all_toggle")
                        ) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = stringResource(
                                    if (allSelected) R.string.compare_library_deselect_all
                                    else R.string.compare_library_select_all
                                )
                            )
                        }
                        IconButton(
                            onClick = {
                                val ids = selectedSessionIds.toList()
                                val suggestedFilename = if (ids.size == 1) {
                                    context.getString(R.string.session_backup_filename_single, ids[0])
                                } else {
                                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                                        .format(Date())
                                    context.getString(R.string.session_backup_filename_multi, timestamp)
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
                    }
                )
            }
        }
    ) { innerPadding ->
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
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("compare_library_grid")
            ) {
                items(sessions, key = { it.sessionId }) { session ->
                    CompareSessionTile(
                        session = session,
                        isSelected = session.sessionId in selectedSessionIds,
                        isSelectionMode = selectionMode,
                        onClick = {
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
                                onSessionClick(session)
                            }
                        },
                        onLongClick = {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompareSessionTile(
    session: ScannedSession,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timestamp = formatTimestamp(session.timestamp)
    val tileDescription = stringResource(
        R.string.compare_library_session_content_description,
        timestamp
    )
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
                if (!session.title.isNullOrEmpty()) {
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
                } else {
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
    }
}

@Composable
private fun formatTimestamp(timestampMs: Long): String =
    remember(timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(timestampMs))
    }

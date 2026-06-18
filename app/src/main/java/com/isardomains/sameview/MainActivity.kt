package com.isardomains.sameview

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.isardomains.sameview.ui.camera.CameraScreen
import com.isardomains.sameview.ui.camera.CameraViewModel
import com.isardomains.sameview.ui.camera.UiEvent
import com.isardomains.sameview.ui.about.AboutScreenRoute
import com.isardomains.sameview.ui.compare.CompareLibraryScreen
import com.isardomains.sameview.ui.compare.CompareScreen
import com.isardomains.sameview.ui.compare.EditSessionEvent
import com.isardomains.sameview.ui.compare.EditSessionScreen
import com.isardomains.sameview.ui.compare.EditSessionViewModel
import com.isardomains.sameview.ui.settings.SettingsScreen
import com.isardomains.sameview.ui.theme.SameViewTheme
import com.isardomains.sameview.ui.video.CreateVideoScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val ROUTE_CAMERA = "camera"
private const val ROUTE_COMPARE = "compare"
private const val ROUTE_COMPARE_LIBRARY = "compare_library"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_CREATE_VIDEO = "create_video"
private const val ARG_CREATE_VIDEO_SESSION_ID = "sessionId"
private const val ROUTE_CREATE_VIDEO_WITH_ARGS = "$ROUTE_CREATE_VIDEO/{$ARG_CREATE_VIDEO_SESSION_ID}"
private const val ROUTE_EDIT_SESSION = "edit_session"
private const val ARG_EDIT_SESSION_ID = "sessionId"
private const val ROUTE_EDIT_SESSION_WITH_ARGS = "$ROUTE_EDIT_SESSION/{$ARG_EDIT_SESSION_ID}"
private const val ARG_REFERENCE_URI = "referenceUri"
private const val ARG_CAPTURE_URI = "captureUri"
private const val ARG_SESSION_ID = "sessionId"
private const val ARG_TIMESTAMP = "timestamp"
private const val ARG_REFERENCE_DATE = "referenceDate"
private const val ROUTE_COMPARE_WITH_ARGS =
    "$ROUTE_COMPARE?$ARG_REFERENCE_URI={$ARG_REFERENCE_URI}&$ARG_CAPTURE_URI={$ARG_CAPTURE_URI}" +
        "&$ARG_SESSION_ID={$ARG_SESSION_ID}&$ARG_TIMESTAMP={$ARG_TIMESTAMP}&$ARG_REFERENCE_DATE={$ARG_REFERENCE_DATE}"

/**
 * The single activity for the SameView app.
 *
 * Hosts the Compose [NavHost] and serves as the Hilt entry point.
 * All navigation destinations are declared here.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            SameViewTheme {
                val navController = rememberNavController()
                val windowSizeClass = calculateWindowSizeClass(this)
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_CAMERA
                ) {
                    composable(ROUTE_CAMERA) {
                        CameraScreen(
                            onCompareImages = { input ->
                                val sessionId = input.sessionId
                                val timestamp = input.timestamp
                                navController.navigate(
                                    if (sessionId != null && timestamp != null) {
                                        compareRoute(
                                            referenceImageUri = input.referenceImageUri,
                                            captureImageUri = input.captureImageUri,
                                            sessionId = sessionId,
                                            timestamp = timestamp,
                                            referenceDate = input.referenceDate
                                        )
                                    } else {
                                        compareRoute(
                                            referenceImageUri = input.referenceImageUri,
                                            captureImageUri = input.captureImageUri,
                                            referenceDate = input.referenceDate
                                        )
                                    }
                                )
                            },
                            onOpenCompareLibrary = {
                                navController.navigate(ROUTE_COMPARE_LIBRARY)
                            },
                            onOpenSettings = {
                                navController.navigate(ROUTE_SETTINGS)
                            },
                            onOpenAbout = {
                                navController.navigate(ROUTE_ABOUT)
                            }
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            windowWidthSizeClass = windowSizeClass.widthSizeClass,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(ROUTE_ABOUT) {
                        AboutScreenRoute(onBack = { navController.popBackStack() })
                    }
                    composable(ROUTE_COMPARE_LIBRARY) { navBackStackEntry ->
                        val cameraEntry = remember(navBackStackEntry) {
                            navController.getBackStackEntry(ROUTE_CAMERA)
                        }
                        val viewModel: CameraViewModel = hiltViewModel(cameraEntry)
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        val snackbarHostState = remember { SnackbarHostState() }
                        var pendingSnackbarEvent by remember { mutableStateOf<UiEvent.ShowSnackbar?>(null) }
                        LaunchedEffect(viewModel) {
                            viewModel.uiEvent.collect { event ->
                                if (event is UiEvent.ShowSnackbar) {
                                    pendingSnackbarEvent = event
                                }
                            }
                        }
                        val pendingMessage = pendingSnackbarEvent?.let { event ->
                            if (event.count != null) stringResource(event.messageResId, event.count)
                            else stringResource(event.messageResId)
                        }
                        LaunchedEffect(pendingSnackbarEvent) {
                            if (pendingMessage != null) {
                                snackbarHostState.showSnackbar(pendingMessage)
                                pendingSnackbarEvent = null
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            CompareLibraryScreen(
                                sessions = uiState.savedSessions,
                                onRefresh = viewModel::refreshSavedSessions,
                                windowWidthSizeClass = windowSizeClass.widthSizeClass,
                                onSessionClick = { session ->
                                    navController.navigate(
                                        compareRoute(
                                            referenceImageUri = session.referenceFileUri,
                                            captureImageUri = session.captureFileUri,
                                            sessionId = session.sessionId,
                                            timestamp = session.timestamp,
                                            referenceDate = session.referenceDate
                                        )
                                    )
                                },
                                onBack = { navController.popBackStack() },
                                onDeleteSessions = viewModel::deleteSessions,
                                onBackupSessions = { sessionIds, uri -> viewModel.backupSessions(sessionIds, uri) },
                                isBackupInProgress = uiState.isBackupInProgress,
                                isDeletionInProgress = uiState.isDeletionInProgress
                            )
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                            )
                        }
                    }
                    composable(
                        route = ROUTE_COMPARE_WITH_ARGS,
                        arguments = listOf(
                            navArgument(ARG_REFERENCE_URI) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument(ARG_CAPTURE_URI) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument(ARG_SESSION_ID) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument(ARG_TIMESTAMP) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument(ARG_REFERENCE_DATE) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val cameraEntry = remember(backStackEntry) {
                            navController.getBackStackEntry(ROUTE_CAMERA)
                        }
                        val viewModel: CameraViewModel = hiltViewModel(cameraEntry)
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        val sessionId = backStackEntry.arguments?.getString(ARG_SESSION_ID)
                        val timestamp =
                            backStackEntry.arguments?.getString(ARG_TIMESTAMP)?.toLongOrNull()
                        val referenceDate = uiState.savedSessions
                            .find { it.sessionId == sessionId }
                            ?.referenceDate
                            ?: backStackEntry.arguments?.getString(ARG_REFERENCE_DATE)
                        val sessionTitle = uiState.savedSessions
                            .find { it.sessionId == sessionId }
                            ?.title
                        val locationDisplayName = uiState.savedSessions
                            .find { it.sessionId == sessionId }
                            ?.locationDisplayName
                        val locationCity = uiState.savedSessions
                            .find { it.sessionId == sessionId }
                            ?.locationCity
                        val locationCountry = uiState.savedSessions
                            .find { it.sessionId == sessionId }
                            ?.locationCountry

                        // Availability check for Create Video: session must have both images.
                        val filesDir = applicationContext.filesDir
                        val isCreateVideoAvailable = remember(sessionId) {
                            if (sessionId == null) false
                            else {
                                val sessionDir = java.io.File(filesDir, "sessions/$sessionId")
                                java.io.File(sessionDir, "reference.jpg").exists() &&
                                    java.io.File(sessionDir, "capture.jpg").exists()
                            }
                        }

                        val snackbarHostState = remember { SnackbarHostState() }
                        val coroutineScope = rememberCoroutineScope()
                        var pendingSnackbarEvent by remember { mutableStateOf<UiEvent.ShowSnackbar?>(null) }

                        LaunchedEffect(viewModel) {
                            viewModel.uiEvent.collect { event ->
                                if (event is UiEvent.ShowSnackbar) {
                                    pendingSnackbarEvent = event
                                }
                            }
                        }

                        val pendingMessage = pendingSnackbarEvent?.let { event ->
                            if (event.count != null) stringResource(event.messageResId, event.count)
                            else stringResource(event.messageResId)
                        }

                        LaunchedEffect(pendingSnackbarEvent) {
                            if (pendingMessage != null) {
                                snackbarHostState.showSnackbar(pendingMessage)
                                pendingSnackbarEvent = null
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            CompareScreen(
                                referenceImageUri = backStackEntry.arguments
                                    ?.getString(ARG_REFERENCE_URI)
                                    ?.let(Uri::parse),
                                captureImageUri = backStackEntry.arguments
                                    ?.getString(ARG_CAPTURE_URI)
                                    ?.let(Uri::parse),
                                onBack = { navController.popBackStack() },
                                timestamp = timestamp,
                                referenceDate = referenceDate,
                                windowWidthSizeClass = windowSizeClass.widthSizeClass,
                                onDelete = if (sessionId != null) {
                                    {
                                        coroutineScope.launch {
                                            val deleted = viewModel.deleteSession(sessionId)
                                            if (deleted) {
                                                navController.popBackStack()
                                            }
                                        }
                                    }
                                } else null,
                                sessionTitle = sessionTitle,
                                locationDisplayName = locationDisplayName,
                                locationCity = locationCity,
                                locationCountry = locationCountry,
                                onEditSession = if (sessionId != null) {
                                    { navController.navigate(editSessionRoute(sessionId)) }
                                } else null,
                                sessionId = sessionId,
                                onBackupSession = if (sessionId != null) {
                                    { uri -> viewModel.backupSingleSession(sessionId, uri) }
                                } else null,
                                isBackupInProgress = uiState.isBackupInProgress,
                                onCreateVideo = if (sessionId != null) {
                                    { navController.navigate(createVideoRoute(sessionId)) }
                                } else null,
                                isCreateVideoAvailable = isCreateVideoAvailable
                            )

                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                            )
                        }
                    }
                    composable(
                        route = ROUTE_CREATE_VIDEO_WITH_ARGS,
                        arguments = listOf(
                            navArgument(ARG_CREATE_VIDEO_SESSION_ID) {
                                type = NavType.StringType
                            }
                        )
                    ) {
                        CreateVideoScreen(
                            onBack = { navController.popBackStack() },
                            windowWidthSizeClass = windowSizeClass.widthSizeClass
                        )
                    }
                    composable(
                        route = ROUTE_EDIT_SESSION_WITH_ARGS,
                        arguments = listOf(
                            navArgument(ARG_EDIT_SESSION_ID) {
                                type = NavType.StringType
                            }
                        )
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getString(ARG_EDIT_SESSION_ID)
                            ?: return@composable
                        val cameraEntry = remember(backStackEntry) {
                            navController.getBackStackEntry(ROUTE_CAMERA)
                        }
                        val cameraViewModel: CameraViewModel = hiltViewModel(cameraEntry)
                        val editSessionViewModel: EditSessionViewModel = hiltViewModel()
                        val snackbarHostState = remember { SnackbarHostState() }
                        val saveFailedMessage = stringResource(R.string.edit_session_save_failed)
                        LaunchedEffect(editSessionViewModel) {
                            editSessionViewModel.events.collect { event ->
                                when (event) {
                                    EditSessionEvent.SaveComplete -> {
                                        cameraViewModel.refreshSavedSessions()
                                        navController.popBackStack()
                                    }
                                    EditSessionEvent.SaveFailed -> {
                                        snackbarHostState.showSnackbar(saveFailedMessage)
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            EditSessionScreen(
                                sessionId = sessionId,
                                onBack = { navController.popBackStack() },
                                viewModel = editSessionViewModel,
                                windowWidthSizeClass = windowSizeClass.widthSizeClass
                            )
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun compareRoute(
    referenceImageUri: Uri,
    captureImageUri: Uri,
    referenceDate: String? = null
): String {
    val base = "$ROUTE_COMPARE?$ARG_REFERENCE_URI=${Uri.encode(referenceImageUri.toString())}" +
        "&$ARG_CAPTURE_URI=${Uri.encode(captureImageUri.toString())}"
    return if (referenceDate != null) "$base&$ARG_REFERENCE_DATE=${Uri.encode(referenceDate)}" else base
}

private fun compareRoute(
    referenceImageUri: Uri,
    captureImageUri: Uri,
    sessionId: String,
    timestamp: Long,
    referenceDate: String? = null
): String {
    val base = "$ROUTE_COMPARE?$ARG_REFERENCE_URI=${Uri.encode(referenceImageUri.toString())}" +
        "&$ARG_CAPTURE_URI=${Uri.encode(captureImageUri.toString())}" +
        "&$ARG_SESSION_ID=${Uri.encode(sessionId)}" +
        "&$ARG_TIMESTAMP=$timestamp"
    return if (referenceDate != null) "$base&$ARG_REFERENCE_DATE=${Uri.encode(referenceDate)}" else base
}

private fun createVideoRoute(sessionId: String): String =
    "$ROUTE_CREATE_VIDEO/${Uri.encode(sessionId)}"

private fun editSessionRoute(sessionId: String): String =
    "$ROUTE_EDIT_SESSION/${Uri.encode(sessionId)}"

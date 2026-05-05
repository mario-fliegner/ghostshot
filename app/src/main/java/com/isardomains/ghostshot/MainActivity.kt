package com.isardomains.ghostshot

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.isardomains.ghostshot.ui.camera.CameraScreen
import com.isardomains.ghostshot.ui.camera.CameraViewModel
import com.isardomains.ghostshot.ui.compare.CompareLibraryScreen
import com.isardomains.ghostshot.ui.compare.CompareScreen
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_CAMERA = "camera"
private const val ROUTE_COMPARE = "compare"
private const val ROUTE_COMPARE_LIBRARY = "compare_library"
private const val ARG_REFERENCE_URI = "referenceUri"
private const val ARG_CAPTURE_URI = "captureUri"
private const val ARG_SESSION_ID = "sessionId"
private const val ARG_TIMESTAMP = "timestamp"
private const val ROUTE_COMPARE_WITH_ARGS =
    "$ROUTE_COMPARE?$ARG_REFERENCE_URI={$ARG_REFERENCE_URI}&$ARG_CAPTURE_URI={$ARG_CAPTURE_URI}" +
        "&$ARG_SESSION_ID={$ARG_SESSION_ID}&$ARG_TIMESTAMP={$ARG_TIMESTAMP}"

/**
 * The single activity for the GhostShot app.
 *
 * Hosts the Compose [NavHost] and serves as the Hilt entry point.
 * All navigation destinations are declared here.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            GhostShotTheme {
                val navController = rememberNavController()
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
                                            timestamp = timestamp
                                        )
                                    } else {
                                        compareRoute(
                                            referenceImageUri = input.referenceImageUri,
                                            captureImageUri = input.captureImageUri
                                        )
                                    }
                                )
                            },
                            onOpenCompareLibrary = {
                                navController.navigate(ROUTE_COMPARE_LIBRARY)
                            }
                        )
                    }
                    composable(ROUTE_COMPARE_LIBRARY) {
                        val cameraEntry = remember(navController) {
                            navController.getBackStackEntry(ROUTE_CAMERA)
                        }
                        val viewModel: CameraViewModel = hiltViewModel(cameraEntry)
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        CompareLibraryScreen(
                            sessions = uiState.savedSessions,
                            onRefresh = viewModel::refreshSavedSessions,
                            onSessionClick = { session ->
                                navController.navigate(
                                    compareRoute(
                                        referenceImageUri = session.referenceFileUri,
                                        captureImageUri = session.captureFileUri,
                                        sessionId = session.sessionId,
                                        timestamp = session.timestamp
                                    )
                                )
                            },
                            onBack = { navController.popBackStack() },
                            onDeleteSessions = viewModel::deleteSessions
                        )
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
                            }
                        )
                    ) { backStackEntry ->
                        val cameraEntry = remember(navController) {
                            navController.getBackStackEntry(ROUTE_CAMERA)
                        }
                        val viewModel: CameraViewModel = hiltViewModel(cameraEntry)
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        val sessionId = backStackEntry.arguments?.getString(ARG_SESSION_ID)
                        val timestamp =
                            backStackEntry.arguments?.getString(ARG_TIMESTAMP)?.toLongOrNull()
                        val sessionTitle = uiState.savedSessions
                            .find { it.sessionId == sessionId }
                            ?.title
                        CompareScreen(
                            referenceImageUri = backStackEntry.arguments
                                ?.getString(ARG_REFERENCE_URI)
                                ?.let(Uri::parse),
                            captureImageUri = backStackEntry.arguments
                                ?.getString(ARG_CAPTURE_URI)
                                ?.let(Uri::parse),
                            onBack = { navController.popBackStack() },
                            timestamp = timestamp,
                            onDelete = if (sessionId != null) {
                                {
                                    viewModel.deleteSessions(listOf(sessionId))
                                    navController.popBackStack()
                                }
                            } else null,
                            sessionTitle = sessionTitle,
                            onSaveTitle = if (sessionId != null) {
                                { title -> viewModel.updateSessionTitle(sessionId, title) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

private fun compareRoute(referenceImageUri: Uri, captureImageUri: Uri): String =
    "$ROUTE_COMPARE?$ARG_REFERENCE_URI=${Uri.encode(referenceImageUri.toString())}" +
        "&$ARG_CAPTURE_URI=${Uri.encode(captureImageUri.toString())}"

private fun compareRoute(
    referenceImageUri: Uri,
    captureImageUri: Uri,
    sessionId: String,
    timestamp: Long
): String =
    "$ROUTE_COMPARE?$ARG_REFERENCE_URI=${Uri.encode(referenceImageUri.toString())}" +
        "&$ARG_CAPTURE_URI=${Uri.encode(captureImageUri.toString())}" +
        "&$ARG_SESSION_ID=${Uri.encode(sessionId)}" +
        "&$ARG_TIMESTAMP=$timestamp"

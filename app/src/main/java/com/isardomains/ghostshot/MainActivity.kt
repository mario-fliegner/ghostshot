package com.isardomains.ghostshot

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.isardomains.ghostshot.ui.camera.CameraScreen
import com.isardomains.ghostshot.ui.compare.CompareScreen
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_CAMERA = "camera"
private const val ROUTE_COMPARE = "compare"
private const val ARG_REFERENCE_URI = "referenceUri"
private const val ARG_CAPTURE_URI = "captureUri"
private const val ROUTE_COMPARE_WITH_ARGS =
    "$ROUTE_COMPARE?$ARG_REFERENCE_URI={$ARG_REFERENCE_URI}&$ARG_CAPTURE_URI={$ARG_CAPTURE_URI}"

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
        enableEdgeToEdge()
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
                                navController.navigate(
                                    compareRoute(
                                        referenceImageUri = input.referenceImageUri,
                                        captureImageUri = input.captureImageUri
                                    )
                                )
                            }
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
                            }
                        )
                    ) { backStackEntry ->
                        CompareScreen(
                            referenceImageUri = backStackEntry.arguments
                                ?.getString(ARG_REFERENCE_URI)
                                ?.let(Uri::parse),
                            captureImageUri = backStackEntry.arguments
                                ?.getString(ARG_CAPTURE_URI)
                                ?.let(Uri::parse),
                            onBack = { navController.popBackStack() }
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

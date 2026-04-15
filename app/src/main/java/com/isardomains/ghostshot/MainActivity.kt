package com.isardomains.ghostshot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.isardomains.ghostshot.ui.camera.CameraScreen
import com.isardomains.ghostshot.ui.theme.GhostShotTheme
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_CAMERA = "camera"

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
                        CameraScreen()
                    }
                }
            }
        }
    }
}

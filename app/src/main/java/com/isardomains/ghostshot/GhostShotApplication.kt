package com.isardomains.ghostshot

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class required by Hilt for dependency injection setup.
 * Must be declared in AndroidManifest.xml via android:name.
 */
@HiltAndroidApp
class GhostShotApplication : Application()

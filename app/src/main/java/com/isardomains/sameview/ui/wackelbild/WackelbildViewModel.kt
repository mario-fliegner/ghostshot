// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildViewModel.kt
package com.isardomains.sameview.ui.wackelbild

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for [WackelbildScreen].
 *
 * Block 2 scope only: resolves the current session's persisted `reference.jpg` for the local
 * preview. No metadata parsing, no date/sensor/network/upload state — those belong to later
 * DeinWackelbild implementation blocks.
 */
@HiltViewModel
class WackelbildViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    /**
     * The session's persisted reference image. Read-only — this feature never writes to or
     * mutates any persisted session file.
     */
    val referenceFile: File = File(context.filesDir, "sessions/$sessionId/reference.jpg")
}

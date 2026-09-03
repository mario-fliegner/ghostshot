// path: app/src/main/java/com/isardomains/sameview/ui/wackelbild/WackelbildCustomTabLauncher.kt
package com.isardomains.sameview.ui.wackelbild

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Thin wrapper for opening the DeinWackelbild checkout URL in an Android Custom Tab.
 *
 * [launch] passes [url] through verbatim -- no reconstruction, no query/fragment/host
 * modification -- since the checkout URL is already validated upstream (Block 7's response
 * parser). No HTTPS re-validation is performed here; this class only handles the launch
 * mechanics and the single distinguishable local failure (no Custom Tab/browser provider
 * available), leaving every other outcome to propagate normally.
 *
 * Not called from any production code path yet -- wiring belongs to a later block.
 */
class WackelbildCustomTabLauncher {

    /**
     * Launches [url] in an Android Custom Tab. Returns `true` if the launch was invoked
     * successfully, `false` if no Custom Tab/browser provider is available on the device
     * ([ActivityNotFoundException]). No other exception type is caught.
     */
    fun launch(context: Context, url: String): Boolean =
        try {
            CustomTabsIntent.Builder()
                .build()
                .launchUrl(context, Uri.parse(url))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}

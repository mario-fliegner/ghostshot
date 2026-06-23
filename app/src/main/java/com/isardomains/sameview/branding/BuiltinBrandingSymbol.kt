// path: app/src/main/java/com/isardomains/sameview/branding/BuiltinBrandingSymbol.kt
package com.isardomains.sameview.branding

import com.isardomains.sameview.R

/**
 * The complete set of built-in branding symbols available in V1.
 *
 * Each entry has a stable [id] string written to [metadata.json][branding.builtinId]
 * for documentary purposes, and a [drawableRes] referencing the VectorDrawable asset.
 *
 * The [id] values are spec-defined and must not be changed once sessions exist
 * in the field — they are part of the backup/restore contract.
 */
enum class BuiltinBrandingSymbol(
    /** Stable identifier written to metadata.json. */
    val id: String,
    /** VectorDrawable resource for this symbol. */
    val drawableRes: Int
) {
    HEART("heart", R.drawable.ic_branding_heart),
    STAR("star", R.drawable.ic_branding_star),
    CAMERA("camera", R.drawable.ic_branding_camera),
    HOME("home", R.drawable.ic_branding_home),
    PIN("pin", R.drawable.ic_branding_pin),
    FIRE("fire", R.drawable.ic_branding_fire);

    companion object {
        /** Returns the symbol whose [id] matches [id], or null if unrecognized. */
        fun fromId(id: String): BuiltinBrandingSymbol? = entries.firstOrNull { it.id == id }
    }
}

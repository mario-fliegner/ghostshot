package com.isardomains.sameview.ui.camera

/** Converts a geographic bearing and device azimuth to a screen-relative display bearing. */
object DirectionArrowCalculator {

    /**
     * Computes the bearing to display on the direction arrow.
     *
     * @param geoBearing Geographic bearing from current position to target, 0–360°, 0° = North.
     * @param azimuth Device azimuth from the compass sensor, 0–360°, 0° = North.
     * @return Screen-relative bearing: 0° = device camera points toward target,
     *   90° = target is 90° to the right of the current camera direction.
     */
    fun computeDisplayBearing(geoBearing: Float, azimuth: Float): Float =
        ((geoBearing - azimuth + 360f) % 360f)
}

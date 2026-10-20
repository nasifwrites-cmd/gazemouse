package com.example.data.models

/**
 * Represents a single calibration point on screen with the captured eye pupil data.
 */
data class CalibrationPoint(
    val index: Int,
    val screenXRatio: Float, // 0.0 to 1.0 (relative screen width)
    val screenYRatio: Float, // 0.0 to 1.0 (relative screen height)
    val pupilX: Float,       // Normalized pupil center X in eye window
    val pupilY: Float,       // Normalized pupil center Y in eye window
    val sampleCount: Int = 1
)

/**
 * 2D Affine / Linear transformation parameters mapping pupil (px, py) -> screen (sx, sy).
 * sx = a0 + a1 * px + a2 * py
 * sy = b0 + b1 * px + b2 * py
 */
data class CalibrationMatrix(
    val a0: Float = 0.5f,
    val a1: Float = 1.0f,
    val a2: Float = 0.0f,
    val b0: Float = 0.5f,
    val b1: Float = 0.0f,
    val b2: Float = 1.0f,
    val minPupilX: Float = 0.2f,
    val maxPupilX: Float = 0.8f,
    val minPupilY: Float = 0.2f,
    val maxPupilY: Float = 0.8f,
    val isCalibrated: Boolean = false
) {
    /**
     * Map raw pupil normalized coordinate to screen coordinates in pixels.
     */
    fun mapToScreen(
        rawPupilX: Float,
        rawPupilY: Float,
        screenWidth: Float,
        screenHeight: Float,
        sensitivityX: Float = 1.0f,
        sensitivityY: Float = 1.0f,
        invertX: Boolean = false,
        invertY: Boolean = false
    ): Pair<Float, Float> {
        var nx = if (maxPupilX > minPupilX) {
            (rawPupilX - minPupilX) / (maxPupilX - minPupilX)
        } else {
            rawPupilX
        }

        var ny = if (maxPupilY > minPupilY) {
            (rawPupilY - minPupilY) / (maxPupilY - minPupilY)
        } else {
            rawPupilY
        }

        // Apply affine coefficients if available
        if (isCalibrated) {
            val mappedX = a0 + a1 * (rawPupilX - 0.5f) + a2 * (rawPupilY - 0.5f)
            val mappedY = b0 + b1 * (rawPupilX - 0.5f) + b2 * (rawPupilY - 0.5f)
            nx = mappedX
            ny = mappedY
        }

        // Apply sensitivity centered around 0.5
        val centeredX = (nx - 0.5f) * sensitivityX + 0.5f
        val centeredY = (ny - 0.5f) * sensitivityY + 0.5f

        val finalX = if (invertX) 1.0f - centeredX else centeredX
        val finalY = if (invertY) 1.0f - centeredY else centeredY

        val clampedX = finalX.coerceIn(0.02f, 0.98f) * screenWidth
        val clampedY = finalY.coerceIn(0.02f, 0.98f) * screenHeight

        return Pair(clampedX, clampedY)
    }
}

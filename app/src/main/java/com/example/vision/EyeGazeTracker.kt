package com.example.vision

import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.data.models.CalibrationMatrix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Real-time eye feature detection and gaze coordinate mapper.
 * Runs 100% locally on front camera frames using luminance gradient analysis.
 */
class EyeGazeTracker {

    data class EyeFeatures(
        val leftPupilX: Float = 0.5f,
        val leftPupilY: Float = 0.5f,
        val rightPupilX: Float = 0.5f,
        val rightPupilY: Float = 0.5f,
        val avgPupilX: Float = 0.5f,
        val avgPupilY: Float = 0.5f,
        val isLeftEyeOpen: Boolean = true,
        val isRightEyeOpen: Boolean = true,
        val frameWidth: Int = 0,
        val frameHeight: Int = 0,
        val leftEyeBox: Rect = Rect(),
        val rightEyeBox: Rect = Rect()
    )

    private val _eyeFeatures = MutableStateFlow(EyeFeatures())
    val eyeFeatures = _eyeFeatures.asStateFlow()

    private val _rawGaze = MutableStateFlow(Pair(0.5f, 0.5f))
    val rawGaze = _rawGaze.asStateFlow()

    private val _smoothedCursor = MutableStateFlow(Pair(500f, 1000f))
    val smoothedCursor = _smoothedCursor.asStateFlow()

    private var activeMatrix = CalibrationMatrix()

    // Sensitivity & tuning parameters
    var smoothingAlpha = 0.22f
    var sensitivityX = 1.6f
    var sensitivityY = 1.8f
    var invertX = false
    var invertY = false

    var screenWidthPx = 1080f
    var screenHeightPx = 2400f

    fun updateScreenDimensions(width: Float, height: Float) {
        if (width > 0 && height > 0) {
            screenWidthPx = width
            screenHeightPx = height
        }
    }

    fun setCalibrationMatrix(matrix: CalibrationMatrix) {
        activeMatrix = matrix
    }

    fun getCalibrationMatrix(): CalibrationMatrix = activeMatrix

    /**
     * CameraX ImageAnalysis Analyzer for front-camera frames.
     */
    fun createAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            try {
                processFrame(imageProxy)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun processFrame(image: ImageProxy) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        if (width <= 0 || height <= 0) return

        // Front camera eye regions relative to frame orientation
        // User's face is centered in the frame.
        // We crop left and right eye search windows.
        val eyeTop = (height * 0.26f).toInt()
        val eyeBottom = (height * 0.54f).toInt()
        val leftEyeLeft = (width * 0.20f).toInt()
        val leftEyeRight = (width * 0.48f).toInt()
        val rightEyeLeft = (width * 0.52f).toInt()
        val rightEyeRight = (width * 0.80f).toInt()

        val leftBox = Rect(leftEyeLeft, eyeTop, leftEyeRight, eyeBottom)
        val rightBox = Rect(rightEyeLeft, eyeTop, rightEyeRight, eyeBottom)

        val leftPupil = findPupilCentroid(buffer, leftBox, width, height, rowStride, pixelStride)
        val rightPupil = findPupilCentroid(buffer, rightBox, width, height, rowStride, pixelStride)

        val avgX = (leftPupil.first + rightPupil.first) * 0.5f
        val avgY = (leftPupil.second + rightPupil.second) * 0.5f

        _eyeFeatures.value = EyeFeatures(
            leftPupilX = leftPupil.first,
            leftPupilY = leftPupil.second,
            rightPupilX = rightPupil.first,
            rightPupilY = rightPupil.second,
            avgPupilX = avgX,
            avgPupilY = avgY,
            isLeftEyeOpen = leftPupil.third,
            isRightEyeOpen = rightPupil.third,
            frameWidth = width,
            frameHeight = height,
            leftEyeBox = leftBox,
            rightEyeBox = rightBox
        )

        _rawGaze.value = Pair(avgX, avgY)

        // Map to screen cursor coordinates
        val (targetScreenX, targetScreenY) = activeMatrix.mapToScreen(
            rawPupilX = avgX,
            rawPupilY = avgY,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            sensitivityX = sensitivityX,
            sensitivityY = sensitivityY,
            invertX = invertX,
            invertY = invertY
        )

        // Adaptive smoothing: high responsiveness on rapid jumps, smooth stability when holding gaze
        val currentX = _smoothedCursor.value.first
        val currentY = _smoothedCursor.value.second
        val dist = kotlin.math.hypot(targetScreenX - currentX, targetScreenY - currentY)
        val dynamicAlpha = if (dist > 300f) {
            (smoothingAlpha * 2.2f).coerceAtMost(0.65f)
        } else {
            smoothingAlpha
        }

        val smoothedX = currentX + dynamicAlpha * (targetScreenX - currentX)
        val smoothedY = currentY + dynamicAlpha * (targetScreenY - currentY)

        _smoothedCursor.value = Pair(smoothedX, smoothedY)
    }

    /**
     * Subpixel pupil detection using inverted luminance gradient centroid.
     * Returns Triple(normalizedX, normalizedY, isOpen)
     */
    private fun findPupilCentroid(
        buffer: ByteBuffer,
        box: Rect,
        frameWidth: Int,
        frameHeight: Int,
        rowStride: Int,
        pixelStride: Int
    ): Triple<Float, Float, Boolean> {
        val boxWidth = box.width()
        val boxHeight = box.height()
        if (boxWidth <= 4 || boxHeight <= 4) return Triple(0.5f, 0.5f, true)

        var minLum = 255
        var sumLum = 0L
        val step = 2 // Sample every 2nd pixel for 60fps performance
        var totalSamples = 0

        val startX = box.left.coerceIn(0, frameWidth - 1)
        val endX = box.right.coerceIn(0, frameWidth - 1)
        val startY = box.top.coerceIn(0, frameHeight - 1)
        val endY = box.bottom.coerceIn(0, frameHeight - 1)

        // Pass 1: Find min luminance and average in eye window
        for (y in startY until endY step step) {
            val rowOffset = y * rowStride
            for (x in startX until endX step step) {
                val index = rowOffset + x * pixelStride
                if (index < buffer.limit()) {
                    val lum = buffer.get(index).toInt() and 0xFF
                    if (lum < minLum) minLum = lum
                    sumLum += lum
                    totalSamples++
                }
            }
        }

        if (totalSamples == 0) return Triple(0.5f, 0.5f, true)
        val avgLum = (sumLum / totalSamples).toInt()

        // Pupil threshold: darker than 25% gradient above minLum
        val pupilThreshold = minLum + ((avgLum - minLum) * 0.32f).toInt()

        // Pass 2: Weighted Center of Mass for darkest cluster (pupil)
        var weightedSumX = 0.0
        var weightedSumY = 0.0
        var totalWeight = 0.0
        var darkPixelCount = 0

        for (y in startY until endY step step) {
            val rowOffset = y * rowStride
            for (x in startX until endX step step) {
                val index = rowOffset + x * pixelStride
                if (index < buffer.limit()) {
                    val lum = buffer.get(index).toInt() and 0xFF
                    if (lum <= pupilThreshold) {
                        val weight = (pupilThreshold - lum + 1).toDouble()
                        val relX = (x - startX).toDouble() / boxWidth
                        val relY = (y - startY).toDouble() / boxHeight

                        weightedSumX += relX * weight
                        weightedSumY += relY * weight
                        totalWeight += weight
                        darkPixelCount++
                    }
                }
            }
        }

        val isOpen = darkPixelCount > 4 && (avgLum - minLum) > 12

        return if (totalWeight > 0.0) {
            val cx = (weightedSumX / totalWeight).toFloat().coerceIn(0.1f, 0.9f)
            val cy = (weightedSumY / totalWeight).toFloat().coerceIn(0.1f, 0.9f)
            Triple(cx, cy, isOpen)
        } else {
            Triple(0.5f, 0.5f, isOpen)
        }
    }

    /**
     * Injects synthetic or manual gaze cursor position (useful for previewing or testing).
     */
    fun injectGaze(relX: Float, relY: Float) {
        val targetX = relX.coerceIn(0.02f, 0.98f) * screenWidthPx
        val targetY = relY.coerceIn(0.02f, 0.98f) * screenHeightPx
        _smoothedCursor.value = Pair(targetX, targetY)
        _rawGaze.value = Pair(relX, relY)
    }
}

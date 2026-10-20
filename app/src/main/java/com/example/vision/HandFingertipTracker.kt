package com.example.vision

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.hypot

/**
 * On-device real-time Fingertip Mouse Tracker with Two-Finger Scrolling and Pinch Click.
 * Processes front camera frames locally using skin-chroma and convexity analysis.
 */
class HandFingertipTracker {

    data class FingerState(
        val isHandDetected: Boolean = false,
        val primaryTipX: Float = 0.5f,
        val primaryTipY: Float = 0.5f,
        val secondaryTipX: Float = 0.5f,
        val secondaryTipY: Float = 0.5f,
        val fingerDistance: Float = 1.0f,
        val isPinching: Boolean = false,
        val isTwoFingerScrolling: Boolean = false,
        val scrollDeltaPx: Float = 0f,
        val rawPixelCount: Int = 0
    )

    private val _fingerState = MutableStateFlow(FingerState())
    val fingerState = _fingerState.asStateFlow()

    private val _cursorPosition = MutableStateFlow(Pair(540f, 1200f))
    val cursorPosition = _cursorPosition.asStateFlow()

    private val _pinchClickEvents = MutableSharedFlow<Pair<Float, Float>>(extraBufferCapacity = 5)
    val pinchClickEvents = _pinchClickEvents.asSharedFlow()

    // Real-time vertical scroll events (in screen pixels: positive = scroll down, negative = scroll up)
    private val _scrollEvents = MutableSharedFlow<Float>(extraBufferCapacity = 10)
    val scrollEvents = _scrollEvents.asSharedFlow()

    var screenWidthPx = 1080f
    var screenHeightPx = 2400f
    var smoothingAlpha = 0.38f
    var pinchThreshold = 0.055f // Distance below which fingers are pinching together
    var scrollSensitivity = 1600f // Multiplier for vertical finger delta to screen pixels
    var sensitivity = 1.4f

    private var wasPinchingLastFrame = false
    private var lastPinchTimeMs = 0L
    private val pinchDebounceMs = 280L

    private var previousTwoFingerY: Float? = null

    fun updateScreenDimensions(width: Float, height: Float) {
        if (width > 0 && height > 0) {
            screenWidthPx = width
            screenHeightPx = height
        }
    }

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
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val width = image.width
        val height = image.height

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        if (width <= 0 || height <= 0) return

        val step = 4
        var topSkinY = height
        var topSkinX = width / 2

        var secondTopY = height
        var secondTopX = width / 2

        var skinPixelCount = 0

        for (y in (height * 0.10f).toInt() until (height * 0.90f).toInt() step step) {
            val yOffset = y * yRowStride
            val uvY = y / 2
            val uOffset = uvY * uRowStride
            val vOffset = uvY * vRowStride

            for (x in (width * 0.05f).toInt() until (width * 0.95f).toInt() step step) {
                val yIdx = yOffset + x * yPixelStride
                val uvX = x / 2
                val uIdx = uOffset + uvX * uPixelStride
                val vIdx = vOffset + uvX * vPixelStride

                if (yIdx < yBuffer.limit() && uIdx < uBuffer.limit() && vIdx < vBuffer.limit()) {
                    val yVal = yBuffer.get(yIdx).toInt() and 0xFF
                    val uVal = uBuffer.get(uIdx).toInt() and 0xFF
                    val vVal = vBuffer.get(vIdx).toInt() and 0xFF

                    val isSkin = yVal in 40..235 && uVal in 75..135 && vVal in 130..185

                    if (isSkin) {
                        skinPixelCount++
                        val mirroredX = width - 1 - x

                        if (y < topSkinY) {
                            val dx = abs(mirroredX - topSkinX)
                            if (dx > (width * 0.07f) && topSkinY < height) {
                                secondTopY = topSkinY
                                secondTopX = topSkinX
                            }
                            topSkinY = y
                            topSkinX = mirroredX
                        } else if (y < secondTopY) {
                            val dx = abs(mirroredX - topSkinX)
                            if (dx > (width * 0.07f)) {
                                secondTopY = y
                                secondTopX = mirroredX
                            }
                        }
                    }
                }
            }
        }

        val isHandDetected = skinPixelCount > 100

        if (isHandDetected) {
            val normPrimaryX = (topSkinX.toFloat() / width).coerceIn(0.05f, 0.95f)
            val normPrimaryY = (topSkinY.toFloat() / height).coerceIn(0.05f, 0.95f)

            val hasSecondFinger = secondTopY < height - 10
            val normSecondaryX = if (hasSecondFinger) {
                (secondTopX.toFloat() / width).coerceIn(0.05f, 0.95f)
            } else normPrimaryX

            val normSecondaryY = if (hasSecondFinger) {
                (secondTopY.toFloat() / height).coerceIn(0.05f, 0.95f)
            } else normPrimaryY

            val distance = hypot(normPrimaryX - normSecondaryX, normPrimaryY - normSecondaryY)

            // Two gestures:
            // 1. PINCH (click): When two fingers touch together tightly (distance <= pinchThreshold)
            // 2. TWO-FINGER SCROLL: When two fingers are both held up side by side (distance in 0.06f..0.24f and similar vertical height)
            val isPinchingNow = hasSecondFinger && distance <= pinchThreshold

            val isTwoFingerScrolling = hasSecondFinger &&
                    distance in 0.06f..0.26f &&
                    abs(normPrimaryY - normSecondaryY) < 0.12f

            var scrollDelta = 0f
            if (isTwoFingerScrolling) {
                val currentTwoFingerAvgY = (normPrimaryY + normSecondaryY) * 0.5f
                val prev = previousTwoFingerY
                if (prev != null) {
                    val rawDelta = currentTwoFingerAvgY - prev
                    // Filter noise threshold
                    if (abs(rawDelta) > 0.003f) {
                        scrollDelta = rawDelta * scrollSensitivity
                        _scrollEvents.tryEmit(scrollDelta)
                    }
                }
                previousTwoFingerY = currentTwoFingerAvgY
            } else {
                previousTwoFingerY = null
            }

            _fingerState.value = FingerState(
                isHandDetected = true,
                primaryTipX = normPrimaryX,
                primaryTipY = normPrimaryY,
                secondaryTipX = normSecondaryX,
                secondaryTipY = normSecondaryY,
                fingerDistance = distance,
                isPinching = isPinchingNow,
                isTwoFingerScrolling = isTwoFingerScrolling,
                scrollDeltaPx = scrollDelta,
                rawPixelCount = skinPixelCount
            )

            // Update Cursor Position following primary fingertip
            val centeredX = (normPrimaryX - 0.5f) * sensitivity + 0.5f
            val centeredY = (normPrimaryY - 0.5f) * sensitivity + 0.5f

            val targetCursorX = (centeredX.coerceIn(0.02f, 0.98f)) * screenWidthPx
            val targetCursorY = (centeredY.coerceIn(0.02f, 0.98f)) * screenHeightPx

            val currentX = _cursorPosition.value.first
            val currentY = _cursorPosition.value.second

            val smoothedX = currentX + smoothingAlpha * (targetCursorX - currentX)
            val smoothedY = currentY + smoothingAlpha * (targetCursorY - currentY)
            _cursorPosition.value = Pair(smoothedX, smoothedY)

            // Trigger pinch click
            val now = System.currentTimeMillis()
            if (isPinchingNow && !wasPinchingLastFrame && (now - lastPinchTimeMs > pinchDebounceMs)) {
                lastPinchTimeMs = now
                _pinchClickEvents.tryEmit(Pair(smoothedX, smoothedY))
            }
            wasPinchingLastFrame = isPinchingNow
        } else {
            previousTwoFingerY = null
            _fingerState.value = _fingerState.value.copy(
                isHandDetected = false,
                isTwoFingerScrolling = false,
                isPinching = false
            )
            wasPinchingLastFrame = false
        }
    }

    /**
     * Injects synthetic or touch finger position (useful for simulator/manual guidance).
     */
    fun injectFinger(normX: Float, normY: Float, isPinching: Boolean = false, isScrolling: Boolean = false, deltaY: Float = 0f) {
        val targetX = normX.coerceIn(0.02f, 0.98f) * screenWidthPx
        val targetY = normY.coerceIn(0.02f, 0.98f) * screenHeightPx
        _cursorPosition.value = Pair(targetX, targetY)

        val secX = if (isPinching) normX else (normX + 0.12f).coerceAtMost(0.95f)
        val secY = normY
        val dist = if (isPinching) 0.02f else 0.12f

        _fingerState.value = FingerState(
            isHandDetected = true,
            primaryTipX = normX,
            primaryTipY = normY,
            secondaryTipX = secX,
            secondaryTipY = secY,
            fingerDistance = dist,
            isPinching = isPinching,
            isTwoFingerScrolling = isScrolling,
            scrollDeltaPx = deltaY,
            rawPixelCount = 500
        )

        if (isPinching && !wasPinchingLastFrame) {
            _pinchClickEvents.tryEmit(Pair(targetX, targetY))
        }
        if (isScrolling && abs(deltaY) > 1f) {
            _scrollEvents.tryEmit(deltaY)
        }
        wasPinchingLastFrame = isPinching
    }
}

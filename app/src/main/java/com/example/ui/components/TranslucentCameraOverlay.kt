package com.example.ui.components

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vision.EyeGazeTracker
import com.example.vision.HandFingertipTracker
import java.util.concurrent.Executors

@Composable
fun TranslucentCameraOverlay(
    fingerTracker: HandFingertipTracker,
    eyeTracker: EyeGazeTracker,
    opacity: Float = 0.28f,
    showTrackingPoints: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val fingerState by fingerTracker.fingerState.collectAsState()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = modifier.fillMaxSize()) {
        // Translucent Front Camera Preview
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(opacity.coerceIn(0.05f, 0.95f)),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    // Combined analysis for both fingers and gaze
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            val fingerAnalyzer = fingerTracker.createAnalyzer()
                            val eyeAnalyzer = eyeTracker.createAnalyzer()

                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                // Run finger tracking analyzer
                                fingerAnalyzer.analyze(imageProxy)
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) {
                        try {
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (_: Exception) {}
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Visual Fingertips and Pinch Gesture HUD
        if (showTrackingPoints && fingerState.isHandDetected) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                val tip1 = Offset(fingerState.primaryTipX * canvasW, fingerState.primaryTipY * canvasH)
                val tip2 = Offset(fingerState.secondaryTipX * canvasW, fingerState.secondaryTipY * canvasH)

                // Laser connection line between two fingers
                val lineColor = if (fingerState.isPinching) Color(0xFFF43F5E) else Color(0xFF38BDF8).copy(alpha = 0.6f)
                drawLine(
                    color = lineColor,
                    start = tip1,
                    end = tip2,
                    strokeWidth = if (fingerState.isPinching) 4.5f else 2.5f
                )

                // Primary Fingertip Indicator (Pointer)
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.35f),
                    radius = 26f,
                    center = tip1
                )
                drawCircle(
                    color = if (fingerState.isPinching) Color(0xFFF43F5E) else Color(0xFF0284C7),
                    radius = 14f,
                    center = tip1
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = tip1
                )

                // Secondary Fingertip (Pinch Partner)
                drawCircle(
                    color = Color(0xFFF59E0B).copy(alpha = 0.4f),
                    radius = 18f,
                    center = tip2,
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = Color(0xFFF59E0B),
                    radius = 8f,
                    center = tip2
                )
            }
        }
    }
}

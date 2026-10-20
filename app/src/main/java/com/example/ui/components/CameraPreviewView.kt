package com.example.ui.components

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vision.EyeGazeTracker
import java.util.concurrent.Executors

@Composable
fun CameraPreviewView(
    tracker: EyeGazeTracker,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val eyeFeatures by tracker.eyeFeatures.collectAsState()

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, tracker.createAnalyzer())
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
                        // Fallback to back camera if front camera is not present
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

        // Overlay drawing eye bounding boxes and pupil indicators
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // Left eye region overlay
            val leftBoxX = canvasW * 0.20f
            val leftBoxY = canvasH * 0.26f
            val leftBoxW = canvasW * 0.28f
            val leftBoxH = canvasH * 0.28f

            drawRect(
                color = Color(0x6638BDF8),
                topLeft = Offset(leftBoxX, leftBoxY),
                size = Size(leftBoxW, leftBoxH),
                style = Stroke(width = 2f)
            )

            // Right eye region overlay
            val rightBoxX = canvasW * 0.52f
            val rightBoxY = canvasH * 0.26f
            val rightBoxW = canvasW * 0.28f
            val rightBoxH = canvasH * 0.28f

            drawRect(
                color = Color(0x6638BDF8),
                topLeft = Offset(rightBoxX, rightBoxY),
                size = Size(rightBoxW, rightBoxH),
                style = Stroke(width = 2f)
            )

            // Detected left pupil marker
            val lpX = leftBoxX + (eyeFeatures.leftPupilX * leftBoxW)
            val lpY = leftBoxY + (eyeFeatures.leftPupilY * leftBoxH)
            drawCircle(
                color = Color(0xFFF43F5E),
                radius = 6f,
                center = Offset(lpX, lpY)
            )

            // Detected right pupil marker
            val rpX = rightBoxX + (eyeFeatures.rightPupilX * rightBoxW)
            val rpY = rightBoxY + (eyeFeatures.rightPupilY * rightBoxH)
            drawCircle(
                color = Color(0xFFF43F5E),
                radius = 6f,
                center = Offset(rpX, rpY)
            )
        }

        Text(
            text = "LIVE EYE TRACKING",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

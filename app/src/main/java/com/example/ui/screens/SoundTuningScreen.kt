package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.OverlayMouseService
import com.example.ui.MainViewModel

@Composable
fun SoundTuningScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val decibel by viewModel.currentDecibel.collectAsState()
    val baselineDb by viewModel.audioDetector.baselineDb.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val snapThreshold by viewModel.snapThresholdDb.collectAsState()

    val fingerState by viewModel.fingerState.collectAsState()

    var testSnapCount by remember { mutableIntStateOf(0) }
    var testPinchCount by remember { mutableIntStateOf(0) }

    var smoothingValue by remember { mutableStateOf(viewModel.fingerTracker.smoothingAlpha) }
    var sensX by remember { mutableStateOf(viewModel.fingerTracker.sensitivity) }
    var pinchThresholdVal by remember { mutableStateOf(viewModel.fingerTracker.pinchThreshold) }
    var scrollSensitivityVal by remember { mutableStateOf(viewModel.fingerTracker.scrollSensitivity) }

    LaunchedEffect(Unit) {
        viewModel.audioDetector.snapEvents.collect {
            testSnapCount++
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fingerTracker.pinchClickEvents.collect {
            testPinchCount++
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Two-Finger Air Scroll Settings
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Air Scroll",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Two-Finger Air Scroll Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Scroll Speed / Sensitivity: ${scrollSensitivityVal.toInt()}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Raise 2 fingers in air and move up/down to scroll feeds, articles, and documents.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = scrollSensitivityVal,
                    onValueChange = {
                        scrollSensitivityVal = it
                        viewModel.setScrollSensitivity(it)
                    },
                    valueRange = 800f..3000f,
                    modifier = Modifier.testTag("slider_scroll_sensitivity")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scroll status card
                Surface(
                    color = if (fingerState.isTwoFingerScrolling) Color(0xFF38BDF8).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (fingerState.isTwoFingerScrolling) "↕️ Two-Finger Scroll ACTIVE" else "Scroll Inactive (1 Finger)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (fingerState.isTwoFingerScrolling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Move 2 fingers vertically in front of camera",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.fingerTracker.injectFinger(0.5f, 0.5f, isScrolling = true, deltaY = 150f)
                            },
                            modifier = Modifier.testTag("btn_test_scroll_down")
                        ) {
                            Text("Test Scroll")
                        }
                    }
                }
            }
        }

        // Section 2: Two-Finger Pinch Click Gesture
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Pinch Click",
                        tint = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Two-Finger Pinch Click Gesture",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Bring index fingertip and thumb together to trigger a mouse click.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = if (fingerState.isPinching) Color(0x33F43F5E) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (fingerState.isPinching) "✌️ PINCH DETECTED!" else "Fingers Apart (Moving)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (fingerState.isPinching) Color(0xFFF43F5E) else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Total Pinches Detected: $testPinchCount",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.handlePinchClick(viewModel.cursorPosition.value.first, viewModel.cursorPosition.value.second)
                            },
                            modifier = Modifier.testTag("btn_trigger_pinch_test")
                        ) {
                            Text("Test Pinch")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Pinch Sensitivity: ${(pinchThresholdVal * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = pinchThresholdVal,
                    onValueChange = {
                        pinchThresholdVal = it
                        viewModel.fingerTracker.pinchThreshold = it
                    },
                    valueRange = 0.04f..0.14f,
                    modifier = Modifier.testTag("slider_pinch_threshold")
                )
            }
        }

        // Section 3: Floating System Overlay (Draw Over Other Apps)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Layers, contentDescription = "Overlay", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Draw Over Other Apps",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (OverlayMouseService.isRunning) "Floating mouse pointer active across all apps!"
                            else "Use fingertip pointer over your home screen, browser, and other apps.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                if (!Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    val serviceIntent = Intent(context, OverlayMouseService::class.java)
                                    if (OverlayMouseService.isRunning) {
                                        context.stopService(serviceIntent)
                                    } else {
                                        context.startService(serviceIntent)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("btn_toggle_floating_service")
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Overlay", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (OverlayMouseService.isRunning) "Stop" else "Launch")
                    }
                }
            }
        }

        // Section 4: Acoustic Snap Sound Click
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Sound Detector",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Acoustic Snap / Clap Click",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            if (isListening) viewModel.stopListeningAudio()
                            else viewModel.startListeningAudio()
                        },
                        modifier = Modifier.testTag("btn_toggle_mic")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Mic toggle",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isListening) "Active" else "Off")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Live Volume: ${decibel.toInt()} dB", fontWeight = FontWeight.SemiBold)
                    Text("Floor: ${baselineDb.toInt()} dB", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (decibel / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (decibel >= snapThreshold) Color(0xFFF43F5E) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Snap dB Threshold: ${snapThreshold.toInt()} dB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = snapThreshold,
                    onValueChange = { viewModel.setSnapThreshold(it) },
                    valueRange = 50f..95f,
                    steps = 45,
                    modifier = Modifier.testTag("slider_snap_threshold")
                )
            }
        }

        // Section 5: Pointer Speed & Smoothing
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Sensitivity",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pointer Speed & Smoothing",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Smoothing: ${(smoothingValue * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = smoothingValue,
                    onValueChange = {
                        smoothingValue = it
                        viewModel.setSmoothing(it)
                    },
                    valueRange = 0.08f..0.55f,
                    modifier = Modifier.testTag("slider_smoothing")
                )

                Text(text = "Sensitivity: ${String.format("%.1f", sensX)}x", fontSize = 13.sp)
                Slider(
                    value = sensX,
                    onValueChange = {
                        sensX = it
                        viewModel.setSensitivity(sensX, sensX)
                    },
                    valueRange = 0.8f..3.0f,
                    modifier = Modifier.testTag("slider_sens_x")
                )
            }
        }
    }
}

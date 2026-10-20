package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@Composable
fun SoundTuningScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val decibel by viewModel.currentDecibel.collectAsState()
    val baselineDb by viewModel.audioDetector.baselineDb.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val snapThreshold by viewModel.snapThresholdDb.collectAsState()
    val deltaTrigger by viewModel.audioDetector.deltaTriggerDb.collectAsState()

    var testSnapCount by remember { mutableIntStateOf(0) }
    var smoothingValue by remember { mutableStateOf(viewModel.gazeTracker.smoothingAlpha) }
    var sensX by remember { mutableStateOf(viewModel.gazeTracker.sensitivityX) }
    var sensY by remember { mutableStateOf(viewModel.gazeTracker.sensitivityY) }
    var invX by remember { mutableStateOf(viewModel.gazeTracker.invertX) }
    var invY by remember { mutableStateOf(viewModel.gazeTracker.invertY) }

    LaunchedEffect(Unit) {
        viewModel.audioDetector.snapEvents.collect {
            testSnapCount++
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Acoustic Snap & Sound Trigger
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
                            text = "Acoustic Snap Click Detector",
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

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time Decibel VU Meter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Live Volume: ${decibel.toInt()} dB", fontWeight = FontWeight.SemiBold)
                    Text("Ambient: ${baselineDb.toInt()} dB", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (decibel / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = if (decibel >= snapThreshold) Color(0xFFF43F5E) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Snap Detection Threshold Slider
                Text(
                    text = "Snap dB Threshold: ${snapThreshold.toInt()} dB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Sound must exceed this volume to register as a mouse click.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = snapThreshold,
                    onValueChange = { viewModel.setSnapThreshold(it) },
                    valueRange = 50f..95f,
                    steps = 45,
                    modifier = Modifier.testTag("slider_snap_threshold")
                )

                // Sudden Jump (Delta) Slider
                Text(
                    text = "Sudden Jump Delta: +${deltaTrigger.toInt()} dB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Requires sound to spike rapidly above the room's noise floor.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = deltaTrigger,
                    onValueChange = { viewModel.audioDetector.deltaTriggerDb.value = it },
                    valueRange = 10f..30f,
                    steps = 20,
                    modifier = Modifier.testTag("slider_delta_threshold")
                )

                // Snap Test Area
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            Text("Snaps Detected: $testSnapCount", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Try snapping fingers or clapping!", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(
                            onClick = { viewModel.handleSnapClick(85f) },
                            modifier = Modifier.testTag("btn_trigger_test_click")
                        ) {
                            Text("Test Click")
                        }
                    }
                }
            }
        }

        // Section 2: Gaze & Cursor Tuning
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
                        text = "Gaze Tracking & Cursor Dynamics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cursor Smoothing Slider
                Text(
                    text = "Smoothing Factor: ${(smoothingValue * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Higher value = faster response; Lower value = rock-steady cursor without jitter.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = smoothingValue,
                    onValueChange = {
                        smoothingValue = it
                        viewModel.setSmoothing(it)
                    },
                    valueRange = 0.08f..0.50f,
                    modifier = Modifier.testTag("slider_smoothing")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Sensitivity X
                Text(text = "Horizontal Gain: ${String.format("%.1f", sensX)}x", fontSize = 13.sp)
                Slider(
                    value = sensX,
                    onValueChange = {
                        sensX = it
                        viewModel.setSensitivity(sensX, sensY)
                    },
                    valueRange = 0.8f..3.0f,
                    modifier = Modifier.testTag("slider_sens_x")
                )

                // Sensitivity Y
                Text(text = "Vertical Gain: ${String.format("%.1f", sensY)}x", fontSize = 13.sp)
                Slider(
                    value = sensY,
                    onValueChange = {
                        sensY = it
                        viewModel.setSensitivity(sensX, sensY)
                    },
                    valueRange = 0.8f..3.0f,
                    modifier = Modifier.testTag("slider_sens_y")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Axis Inversion Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Invert Horizontal (X-Axis)", fontSize = 13.sp)
                    Switch(
                        checked = invX,
                        onCheckedChange = {
                            invX = it
                            viewModel.toggleInvertX()
                        },
                        modifier = Modifier.testTag("switch_invert_x")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Invert Vertical (Y-Axis)", fontSize = 13.sp)
                    Switch(
                        checked = invY,
                        onCheckedChange = {
                            invY = it
                            viewModel.toggleInvertY()
                        },
                        modifier = Modifier.testTag("switch_invert_y")
                    )
                }
            }
        }
    }
}

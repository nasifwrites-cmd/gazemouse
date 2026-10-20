package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.components.CameraPreviewView
import com.example.vision.GazeCalibrationEngine

@Composable
fun CalibrationScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val calibrationEngine = viewModel.calibrationEngine
    val calibrationState by calibrationEngine.currentState.collectAsState()
    val currentPointIndex by calibrationEngine.currentPointIndex.collectAsState()
    val progressRatio by calibrationEngine.progressRatio.collectAsState()
    val recordedPoints by calibrationEngine.recordedPoints.collectAsState()
    val lastAccuracy by calibrationEngine.lastComputedAccuracy.collectAsState()
    val eyeFeatures by viewModel.eyeFeatures.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("My Calibration") }

    // Pulsing target animation
    val infiniteTransition = rememberInfiniteTransition(label = "targetPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "targetPulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Grid points rendering canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            calibrationEngine.defaultGridPoints.forEachIndexed { index, (relX, relY) ->
                val center = Offset(relX * canvasW, relY * canvasH)
                val isCurrentTarget = calibrationState != GazeCalibrationEngine.State.IDLE && index == currentPointIndex
                val isRecorded = index < recordedPoints.size

                if (isCurrentTarget) {
                    // Outer pulsing ring for active target
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                        radius = 42f * pulseScale,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF0284C7),
                        radius = 26f,
                        center = center
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 10f,
                        center = center
                    )
                } else if (isRecorded) {
                    // Already captured point
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = 0.3f),
                        radius = 18f,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF10B981),
                        radius = 10f,
                        center = center
                    )
                } else {
                    // Pending grid point
                    drawCircle(
                        color = Color(0x3364748B),
                        radius = 14f,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )
                    drawCircle(
                        color = Color(0x6664748B),
                        radius = 4f,
                        center = center
                    )
                }
            }
        }

        // Top Info Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "9-POINT EYE GAZE CALIBRATION",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (calibrationState) {
                            GazeCalibrationEngine.State.IDLE -> "Press 'Start' and gaze at each dot as it pulses."
                            GazeCalibrationEngine.State.COUNTDOWN -> "Target ${currentPointIndex + 1}/9: Focus your gaze on the dot..."
                            GazeCalibrationEngine.State.RECORDING -> "Recording pupil vector... Hold still!"
                            GazeCalibrationEngine.State.COMPLETED -> "Calibration complete! Accuracy: ${lastAccuracy.toInt()}%"
                        },
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (calibrationState != GazeCalibrationEngine.State.IDLE && calibrationState != GazeCalibrationEngine.State.COMPLETED) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Live Camera Mini PIP
        CameraPreviewView(
            tracker = viewModel.gazeTracker,
            modifier = Modifier
                .align(Alignment.Center)
                .size(120.dp, 160.dp)
        )

        // Center instructions or pupil readout
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color(0xDD0B132B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Pupil: X: ${(eyeFeatures.avgPupilX * 100).toInt()}%  Y: ${(eyeFeatures.avgPupilY * 100).toInt()}%",
                    color = Color(0xFF7DD3FC),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Bottom Action Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (calibrationState == GazeCalibrationEngine.State.IDLE) {
                    Button(
                        onClick = {
                            calibrationEngine.startCalibration { matrix, points, accuracy ->
                                showSaveDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .testTag("btn_start_calibration")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start 9-Point Calibration")
                    }
                } else if (calibrationState == GazeCalibrationEngine.State.COMPLETED) {
                    Button(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .testTag("btn_save_calibration")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Save")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Profile (${lastAccuracy.toInt()}%)")
                    }
                } else {
                    Button(
                        onClick = { calibrationEngine.cancelCalibration() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .testTag("btn_cancel_calibration")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Cancel")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Calibration")
                    }
                }
            }
        }

        // Save Profile Dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Calibration Profile") },
                text = {
                    Column {
                        Text(
                            text = "Accuracy Score: ${lastAccuracy.toInt()}%",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This calibration data will be saved locally to your device so you don't need to recalibrate every time.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text("Profile Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_profile_name")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveCalibrationProfile(
                                name = profileName,
                                matrix = viewModel.gazeTracker.getCalibrationMatrix(),
                                points = recordedPoints,
                                accuracy = lastAccuracy
                            )
                            showSaveDialog = false
                            viewModel.setScreen(AppScreen.PLAYGROUND)
                        },
                        modifier = Modifier.testTag("btn_confirm_save_profile")
                    ) {
                        Text("Save & Open Mouse")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }
    }
}

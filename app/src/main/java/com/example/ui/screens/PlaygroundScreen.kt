package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush as BrushGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.PlaygroundMode
import com.example.ui.components.CameraPreviewView
import com.example.ui.components.VirtualCursorOverlay
import kotlin.math.hypot

@Composable
fun PlaygroundScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val cursor by viewModel.cursorPosition.collectAsState()
    val decibel by viewModel.currentDecibel.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val snapThreshold by viewModel.snapThresholdDb.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val playgroundMode by viewModel.playgroundMode.collectAsState()
    val showCameraPip by viewModel.showCameraPip.collectAsState()

    // Game state
    val score by viewModel.gameScore.collectAsState()
    val streak by viewModel.gameStreak.collectAsState()
    val activeBubble by viewModel.activeBubble.collectAsState()

    // Keypad state
    val keypadDisplay by viewModel.keypadDisplay.collectAsState()

    // Canvas state
    val canvasDots by viewModel.canvasDots.collectAsState()

    // Touch gaze simulation fallback toggle
    var enableTouchGaze by remember { mutableStateOf(false) }

    // Pulsing target animation
    val infiniteTransition = rememberInfiniteTransition(label = "bubblePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                viewModel.updateScreenDimensions(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
            }
            .then(
                if (enableTouchGaze) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val relX = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                            val relY = (change.position.y / size.height).coerceIn(0.05f, 0.95f)
                            viewModel.gazeTracker.injectGaze(relX, relY)
                        }
                    }
                } else Modifier
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top HUD Bar: Sound Decibels & Status
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sound & Snap Status
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = "Microphone",
                                tint = if (decibel >= snapThreshold) Color(0xFFF43F5E) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Snap Sound: ${decibel.toInt()} dB",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (decibel >= snapThreshold) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurface
                            )
                            if (decibel >= snapThreshold) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "⚡ CLICK!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF43F5E)
                                )
                            }
                        }

                        // Calibration status chip
                        AssistChip(
                            onClick = { viewModel.setScreen(AppScreen.CALIBRATION) },
                            label = {
                                Text(
                                    text = if (activeProfile != null) "Calibrated (${activeProfile?.accuracyScore?.toInt()}%)" else "Uncalibrated",
                                    fontSize = 11.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = "Calibration",
                                    tint = if (activeProfile != null) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.testTag("calibration_chip")
                        )
                    }

                    // Decibel Level progress bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { (decibel / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (decibel >= snapThreshold) Color(0xFFF43F5E) else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Snap at ${snapThreshold.toInt()}dB",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Mode Selection Tab Row
            TabRow(
                selectedTabIndex = playgroundMode.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = playgroundMode == PlaygroundMode.TARGET_GAME,
                    onClick = { viewModel.setPlaygroundMode(PlaygroundMode.TARGET_GAME) },
                    text = { Text("Target Pop", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.GpsFixed, contentDescription = "Target Game", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tab_target_game")
                )
                Tab(
                    selected = playgroundMode == PlaygroundMode.VIRTUAL_KEYPAD,
                    onClick = { viewModel.setPlaygroundMode(PlaygroundMode.VIRTUAL_KEYPAD) },
                    text = { Text("Keypad", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Dialpad, contentDescription = "Keypad", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tab_keypad")
                )
                Tab(
                    selected = playgroundMode == PlaygroundMode.EYE_CANVAS,
                    onClick = { viewModel.setPlaygroundMode(PlaygroundMode.EYE_CANVAS) },
                    text = { Text("Eye Paint", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Brush, contentDescription = "Canvas", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tab_canvas")
                )
            }

            // Interactive Playground Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (playgroundMode) {
                    PlaygroundMode.TARGET_GAME -> {
                        // Target Practice Game Canvas
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Score Board Card
                            Card(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.TopStart),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("SCORE", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text("$score", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("STREAK", fontSize = 10.sp, color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                                        Text("x$streak", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF43F5E))
                                    }
                                }
                            }

                            // Instruction hint
                            Text(
                                text = "👀 Look at the glowing bubble\n🫰 Snap fingers or clap to POP!",
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 20.dp)
                            )

                            // Render Target Bubble
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                activeBubble?.let { bubble ->
                                    val bubbleCenter = Offset(
                                        bubble.xRatio * size.width,
                                        bubble.yRatio * size.height
                                    )
                                    val r = bubble.radiusPx * pulseScale

                                    // Outer glow
                                    drawCircle(
                                        color = Color(0x33F43F5E),
                                        radius = r * 1.4f,
                                        center = bubbleCenter
                                    )
                                    // Main Bubble
                                    drawCircle(
                                        brush = BrushGradient.radialGradient(
                                            colors = listOf(Color(0xFFFDA4AF), Color(0xFFE11D48)),
                                            center = bubbleCenter,
                                            radius = r
                                        ),
                                        radius = r,
                                        center = bubbleCenter
                                    )
                                    // Target Ring
                                    drawCircle(
                                        color = Color.White,
                                        radius = r * 0.55f,
                                        center = bubbleCenter,
                                        style = Stroke(width = 3f)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = r * 0.2f,
                                        center = bubbleCenter
                                    )
                                }
                            }
                        }
                    }

                    PlaygroundMode.VIRTUAL_KEYPAD -> {
                        // Keypad Interface
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Look at button & snap to type:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Display readout
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Box(
                                    contentAlignment = Alignment.CenterEnd,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = keypadDisplay.ifEmpty { "0" },
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3x4 Button Grid
                            val buttons = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("C", "0", "⌫")
                            )

                            buttons.forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    row.forEach { label ->
                                        var buttonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                                        val isCursorOver = buttonBounds?.let { b ->
                                            cursor.first in b.left..b.right && cursor.second in b.top..b.bottom
                                        } ?: false

                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .onGloballyPositioned { coords ->
                                                    buttonBounds = coords.boundsInRoot()
                                                }
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isCursorOver) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .border(
                                                    width = if (isCursorOver) 2.5.dp else 1.dp,
                                                    color = if (isCursorOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable { viewModel.onKeypadButtonHit(label) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCursorOver) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PlaygroundMode.EYE_CANVAS -> {
                        // Eye Paint Canvas
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Canvas surface showing all drawn dots
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                canvasDots.forEach { dot ->
                                    drawCircle(
                                        color = Color(dot.colorHex),
                                        radius = dot.radius,
                                        center = Offset(dot.x, dot.y)
                                    )
                                }
                            }

                            // Canvas Toolbar Controls
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.BottomCenter),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { viewModel.cycleCanvasColor() },
                                    modifier = Modifier.testTag("btn_change_color")
                                ) {
                                    Icon(Icons.Default.Brush, contentDescription = "Color", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Next Color")
                                }

                                OutlinedButton(
                                    onClick = { viewModel.clearCanvas() },
                                    modifier = Modifier.testTag("btn_clear_canvas")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear (${canvasDots.size})")
                                }
                            }
                        }
                    }
                }

                // Floating Camera Preview (Picture-in-Picture)
                if (showCameraPip) {
                    CameraPreviewView(
                        tracker = viewModel.gazeTracker,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(width = 110.dp, height = 145.dp)
                    )
                }

                // Render Live Mouse Cursor on top
                VirtualCursorOverlay(
                    cursorX = cursor.first,
                    cursorY = cursor.second,
                    clickRippleFlow = viewModel.clickRipple
                )
            }

            // Bottom Quick Bar: Manual Click Test, PIP Toggle, Touch Simulation
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Manual Click Trigger (allows testing snap click behavior with a button)
                    FilledTonalButton(
                        onClick = { viewModel.handleSnapClick(85f) },
                        modifier = Modifier.testTag("btn_manual_snap")
                    ) {
                        Text("🫰 Test Snap Click")
                    }

                    Row {
                        // Toggle Touch Gaze Guide (helpful for testing)
                        IconButton(
                            onClick = { enableTouchGaze = !enableTouchGaze },
                            modifier = Modifier.testTag("btn_toggle_touch_gaze")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Touch Simulation",
                                tint = if (enableTouchGaze) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Toggle Camera PIP
                        IconButton(
                            onClick = { viewModel.showCameraPip.value = !viewModel.showCameraPip.value },
                            modifier = Modifier.testTag("btn_toggle_camera_pip")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera View",
                                tint = if (showCameraPip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

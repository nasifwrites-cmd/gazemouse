package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.TrackingMode
import com.example.ui.components.CameraPreviewView
import com.example.ui.components.CameraProcessingHost
import com.example.ui.components.VirtualCursorOverlay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

data class FeedItem(
    val id: Int,
    val title: String,
    val category: String,
    val snippet: String,
    val readTime: String,
    val likesCount: Int = 42
)

@Composable
fun PlaygroundScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val cursor by viewModel.cursorPosition.collectAsState()
    val trackingMode by viewModel.trackingMode.collectAsState()
    val fingerState by viewModel.fingerState.collectAsState()

    val decibel by viewModel.currentDecibel.collectAsState()
    val snapThreshold by viewModel.snapThresholdDb.collectAsState()
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

    // Feed Scroll state
    val feedScrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Touch simulation fallback toggle
    var enableTouchGaze by remember { mutableStateOf(false) }

    // Pulsing target animation
    val infiniteTransition = rememberInfiniteTransition(label = "targetPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Listen for Real-Time Two-Finger Scroll Events
    LaunchedEffect(Unit) {
        viewModel.scrollEvents.collect { deltaPx ->
            feedScrollState.scrollBy(deltaPx)
        }
    }

    // Sample Scrollable Feed Data
    val sampleArticles = remember {
        listOf(
            FeedItem(1, "Artificial Intelligence in Modern Medicine", "Health Tech", "New computer vision algorithms are assisting doctors in diagnosing diseases with unprecedented accuracy.", "3 min read", 128),
            FeedItem(2, "Next-Gen Smartphone Gesture Controls", "Hardware", "Air gestures, fingertip tracking, and acoustic click detection are revolutionizing touchless smartphone interaction.", "5 min read", 256),
            FeedItem(3, "Deep Space Exploration with James Webb", "Astronomy", "Stunning new imagery captures galaxies formed over 13 billion years ago at the dawn of the universe.", "4 min read", 380),
            FeedItem(4, "Renewable Energy Breakthroughs for 2026", "Climate", "Next-generation perovskite solar panels achieve over 34% energy conversion efficiency in laboratory trials.", "6 min read", 94),
            FeedItem(5, "Autonomous Robotics and Sensor Fusion", "Robotics", "Combining LiDAR, optical flow, and inertial measurement units enables humanoid robots to navigate complex terrains.", "5 min read", 172),
            FeedItem(6, "The Evolution of Mobile Computing", "Innovation", "From bulky physical keyboards to touchscreens and now fully touchless gesture interfaces, mobile UX is evolving fast.", "4 min read", 310)
        )
    }

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
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val relX = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                            val relY = (change.position.y / size.height).coerceIn(0.05f, 0.95f)

                            val isDraggingScroll = abs(dragAmount.y) > 8f
                            if (trackingMode == TrackingMode.FINGERTIP_TRACKING) {
                                viewModel.fingerTracker.injectFinger(
                                    relX, relY,
                                    isPinching = false,
                                    isScrolling = isDraggingScroll,
                                    deltaY = dragAmount.y * 1.5f
                                )
                            } else {
                                viewModel.gazeTracker.injectGaze(relX, relY)
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        // Headless Background Camera Processor (NO face video displayed on screen)
        CameraProcessingHost(
            fingerTracker = viewModel.fingerTracker,
            eyeTracker = viewModel.gazeTracker
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Status Bar: Tracking Mode, Gesture Status & Decibel
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = trackingMode == TrackingMode.FINGERTIP_TRACKING,
                                onClick = { viewModel.setTrackingMode(TrackingMode.FINGERTIP_TRACKING) },
                                label = { Text("👆 Fingertip", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Fingerprint, contentDescription = "Fingertip", modifier = Modifier.size(14.dp))
                                },
                                modifier = Modifier.testTag("chip_fingertip_mode")
                            )

                            FilterChip(
                                selected = trackingMode == TrackingMode.EYE_GAZE_TRACKING,
                                onClick = { viewModel.setTrackingMode(TrackingMode.EYE_GAZE_TRACKING) },
                                label = { Text("👁️ Eye Gaze", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Visibility, contentDescription = "Eye", modifier = Modifier.size(14.dp))
                                },
                                modifier = Modifier.testTag("chip_eyegaze_mode")
                            )
                        }

                        // Live Gesture Pill: Pinch Click / Two-finger Scrolling / Hand Active
                        when {
                            fingerState.isPinching -> {
                                Surface(
                                    color = Color(0xFFF43F5E),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "✌️ PINCH CLICK!",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            fingerState.isTwoFingerScrolling -> {
                                Surface(
                                    color = Color(0xFF38BDF8),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "↕️ SCROLLING",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            fingerState.isHandDetected -> {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Fingertip Active",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "Point finger to aim",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Gesture Guide Text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1 Finger = Pointer  •  2 Fingers = Scroll ↕️  •  Pinch = Click ✌️",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Snap: ${decibel.toInt()}dB",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Mode Selection Tab Row (Scroll Feed, Target Game, Keypad, Paint)
            TabRow(
                selectedTabIndex = playgroundMode.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = playgroundMode == PlaygroundMode.SCROLL_FEED,
                    onClick = { viewModel.setPlaygroundMode(PlaygroundMode.SCROLL_FEED) },
                    text = { Text("Scroll Feed", fontSize = 11.sp) },
                    icon = { Icon(Icons.Default.SwapVert, contentDescription = "Scroll Feed", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tab_scroll_feed")
                )
                Tab(
                    selected = playgroundMode == PlaygroundMode.TARGET_GAME,
                    onClick = { viewModel.setPlaygroundMode(PlaygroundMode.TARGET_GAME) },
                    text = { Text("Target Pop", fontSize = 11.sp) },
                    icon = { Icon(Icons.Default.GpsFixed, contentDescription = "Target Game", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tab_target_game")
                )
                Tab(
                    selected = playgroundMode == PlaygroundMode.VIRTUAL_KEYPAD,
                    onClick = { viewModel.setPlaygroundMode(PlaygroundMode.VIRTUAL_KEYPAD) },
                    text = { Text("Keypad", fontSize = 11.sp) },
                    icon = { Icon(Icons.Default.Dialpad, contentDescription = "Keypad", modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("tab_keypad")
                )
                Tab(
                    selected = playgroundMode == PlaygroundMode.EYE_CANVAS,
                    onClick = { viewModel.setPlaygroundMode(PlaygroundMode.EYE_CANVAS) },
                    text = { Text("Paint", fontSize = 11.sp) },
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
                    PlaygroundMode.SCROLL_FEED -> {
                        // Interactive Scrollable Article & Card Reader Feed
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = feedScrollState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.SwapVert, contentDescription = "Scroll", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Two-Finger Air Scroll Demo",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "• Hold 2 fingers up side by side in front of camera\n" +
                                                        "• Move them up ⬆️ or down ⬇️ to scroll this page smoothly!\n" +
                                                        "• Bring two fingers together (pinch) on any card to click and like it!",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                items(sampleArticles) { item ->
                                    var isLiked by remember { mutableStateOf(false) }
                                    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                                    val isCursorOver = cardBounds?.let { b ->
                                        cursor.first in b.left..b.right && cursor.second in b.top..b.bottom
                                    } ?: false

                                    // Auto-trigger on hover and pinch
                                    var wasPinchingOnThisCard by remember { mutableStateOf(false) }
                                    if (isCursorOver && fingerState.isPinching) {
                                        if (!wasPinchingOnThisCard) {
                                            wasPinchingOnThisCard = true
                                            isLiked = !isLiked
                                            viewModel.handlePinchClick(cursor.first, cursor.second)
                                        }
                                    } else {
                                        wasPinchingOnThisCard = false
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCursorOver) MaterialTheme.colorScheme.surfaceVariant
                                            else MaterialTheme.colorScheme.surface
                                        ),
                                        border = BorderStroke(
                                            width = if (isCursorOver) 2.dp else 1.dp,
                                            color = if (isCursorOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coords ->
                                                cardBounds = coords.boundsInRoot()
                                            }
                                            .clickable { isLiked = !isLiked }
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = item.category.uppercase(),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(item.readTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = item.title,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = item.snippet,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 17.sp
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                FilledTonalButton(
                                                    onClick = { isLiked = !isLiked },
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.ThumbUp,
                                                        contentDescription = "Like",
                                                        tint = if (isLiked) Color(0xFFF43F5E) else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isLiked) "Liked (${item.likesCount + 1})" else "Like (${item.likesCount})",
                                                        fontSize = 11.sp
                                                    )
                                                }

                                                Text(
                                                    text = if (isCursorOver) "🎯 Aimed" else "Pinch to interact",
                                                    fontSize = 11.sp,
                                                    color = if (isCursorOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Floating Scroll Up / Down Helpers
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        coroutineScope.launch { feedScrollState.scrollBy(-350f) }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(42.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll Up")
                                }

                                FilledTonalButton(
                                    onClick = {
                                        coroutineScope.launch { feedScrollState.scrollBy(350f) }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(42.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll Down")
                                }
                            }
                        }
                    }

                    PlaygroundMode.TARGET_GAME -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Card(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .align(Alignment.TopStart),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("SCORE", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text("$score", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("STREAK", fontSize = 9.sp, color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                                        Text("x$streak", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF43F5E))
                                    }
                                }
                            }

                            Text(
                                text = "👆 Aim fingertip on bubble • ✌️ Pinch two fingers to POP!",
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 18.dp)
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                activeBubble?.let { bubble ->
                                    val bubbleCenter = Offset(
                                        bubble.xRatio * size.width,
                                        bubble.yRatio * size.height
                                    )
                                    val r = bubble.radiusPx * pulseScale

                                    drawCircle(
                                        color = Color(0x33F43F5E),
                                        radius = r * 1.4f,
                                        center = bubbleCenter
                                    )
                                    drawCircle(
                                        brush = BrushGradient.radialGradient(
                                            colors = listOf(Color(0xFFFDA4AF), Color(0xFFE11D48)),
                                            center = bubbleCenter,
                                            radius = r
                                        ),
                                        radius = r,
                                        center = bubbleCenter
                                    )
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
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Hover fingertip & pinch two fingers together to type:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Box(
                                    contentAlignment = Alignment.CenterEnd,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Text(
                                        text = keypadDisplay.ifEmpty { "0" },
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            val buttons = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("C", "0", "⌫")
                            )

                            buttons.forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(vertical = 5.dp)
                                ) {
                                    row.forEach { label ->
                                        var buttonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                                        val isCursorOver = buttonBounds?.let { b ->
                                            cursor.first in b.left..b.right && cursor.second in b.top..b.bottom
                                        } ?: false

                                        var wasPinchingOnThisButton by remember { mutableStateOf(false) }
                                        if (isCursorOver && fingerState.isPinching) {
                                            if (!wasPinchingOnThisButton) {
                                                wasPinchingOnThisButton = true
                                                viewModel.onKeypadButtonHit(label)
                                            }
                                        } else {
                                            wasPinchingOnThisButton = false
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
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
                                                fontSize = 20.sp,
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                canvasDots.forEach { dot ->
                                    drawCircle(
                                        color = Color(dot.colorHex),
                                        radius = dot.radius,
                                        center = Offset(dot.x, dot.y)
                                    )
                                }
                            }

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

                // Small Optional Camera PIP toggle (only if user explicitly clicks PIP button)
                if (showCameraPip) {
                    CameraPreviewView(
                        tracker = viewModel.gazeTracker,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(width = 90.dp, height = 120.dp)
                    )
                }

                // Render Live Mouse Pointer following fingertip
                VirtualCursorOverlay(
                    cursorX = cursor.first,
                    cursorY = cursor.second,
                    clickRippleFlow = viewModel.clickRipple
                )
            }

            // Bottom Bar: Pinch Click Test & Touch Guide
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.handlePinchClick(cursor.first, cursor.second)
                        },
                        modifier = Modifier.testTag("btn_test_pinch_click")
                    ) {
                        Text("✌️ Test Pinch Click")
                    }

                    Row {
                        IconButton(
                            onClick = { enableTouchGaze = !enableTouchGaze },
                            modifier = Modifier.testTag("btn_toggle_touch_gaze")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Touch Guide",
                                tint = if (enableTouchGaze) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.showCameraPip.value = !viewModel.showCameraPip.value
                            },
                            modifier = Modifier.testTag("btn_toggle_camera_pip")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Toggle Mini Preview",
                                tint = if (showCameraPip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

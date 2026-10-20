package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VirtualCursorOverlay(
    cursorX: Float,
    cursorY: Float,
    clickRippleFlow: SharedFlow<Pair<Float, Float>>,
    modifier: Modifier = Modifier
) {
    val rippleRadius = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0f) }
    val lastClickPos = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        clickRippleFlow.collect { (cx, cy) ->
            lastClickPos.value = Offset(cx, cy)
            rippleRadius.snapTo(10f)
            rippleAlpha.snapTo(0.85f)

            coroutineScope {
                launch {
                    rippleRadius.animateTo(
                        targetValue = 90f,
                        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    rippleAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw click ripple burst if active
            if (rippleAlpha.value > 0.01f) {
                drawCircle(
                    color = Color(0xFFF43F5E).copy(alpha = rippleAlpha.value),
                    radius = rippleRadius.value,
                    center = lastClickPos.value,
                    style = Stroke(width = 4f)
                )
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = rippleAlpha.value * 0.5f),
                    radius = rippleRadius.value * 0.6f,
                    center = lastClickPos.value,
                    style = Stroke(width = 2.5f)
                )
            }

            // Draw Virtual Mouse Reticle & Crosshair
            val center = Offset(cursorX, cursorY)

            // Outer ring
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.85f),
                radius = 24f,
                center = center,
                style = Stroke(width = 2.5f)
            )

            // Inner target dot
            drawCircle(
                color = Color(0xFFF43F5E),
                radius = 6f,
                center = center
            )

            // Center glow
            drawCircle(
                color = Color.White,
                radius = 2.5f,
                center = center
            )

            // Crosshair ticks
            val tickLength = 12f
            val ringRadius = 24f
            // Left tick
            drawLine(
                color = Color(0xFF38BDF8),
                start = Offset(center.x - ringRadius - tickLength, center.y),
                end = Offset(center.x - ringRadius + 2f, center.y),
                strokeWidth = 2f
            )
            // Right tick
            drawLine(
                color = Color(0xFF38BDF8),
                start = Offset(center.x + ringRadius - 2f, center.y),
                end = Offset(center.x + ringRadius + tickLength, center.y),
                strokeWidth = 2f
            )
            // Top tick
            drawLine(
                color = Color(0xFF38BDF8),
                start = Offset(center.x, center.y - ringRadius - tickLength),
                end = Offset(center.x, center.y - ringRadius + 2f),
                strokeWidth = 2f
            )
            // Bottom tick
            drawLine(
                color = Color(0xFF38BDF8),
                start = Offset(center.x, center.y + ringRadius - 2f),
                end = Offset(center.x, center.y + ringRadius + tickLength),
                strokeWidth = 2f
            )
        }

        // Coordinates badge next to cursor
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (cursorX + 32).roundToInt().coerceAtMost(900),
                        (cursorY - 20).roundToInt().coerceAtLeast(40)
                    )
                }
                .background(Color(0xCC0B132B), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${cursorX.toInt()}, ${cursorY.toInt()}",
                color = Color(0xFF7DD3FC),
                fontSize = 10.sp
            )
        }
    }
}

package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun ModernGeminiBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    // Animation state for floating blobs
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val animOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    val baseColor = if (isDark) Color(0xFF0D0D0D) else Color(0xFFF7F9FC)
    val blobColor1 = if (isDark) Color(0xFF1A237E).copy(alpha = 0.4f) else Color(0xFFE3F2FD).copy(alpha = 0.6f)
    val blobColor2 = if (isDark) Color(0xFF311B92).copy(alpha = 0.3f) else Color(0xFFF3E5F5).copy(alpha = 0.5f)
    val dotColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Draw Base
            drawRect(color = baseColor)

            // 2. Draw Animated Mesh Blobs
            drawMeshBlobs(animOffset, blobColor1, blobColor2)

            // 3. Draw Dotted Grid
            drawDottedGrid(dotColor)
        }
        content()
    }
}

private fun DrawScope.drawMeshBlobs(offset: Float, color1: Color, color2: Color) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    // Blob 1 (Top Left)
    val x1 = canvasWidth * (0.2f + 0.1f * sin(offset * 2 * Math.PI.toFloat()))
    val y1 = canvasHeight * (0.2f + 0.15f * sin(offset * Math.PI.toFloat()))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color1, Color.Transparent),
            center = Offset(x1, y1),
            radius = canvasWidth * 0.9f
        ),
        center = Offset(x1, y1),
        radius = canvasWidth * 0.9f
    )

    // Blob 2 (Bottom Right)
    val x2 = canvasWidth * (0.8f - 0.1f * sin(offset * 1.5f * Math.PI.toFloat()))
    val y2 = canvasHeight * (0.8f - 0.2f * sin(offset * 2.5f * Math.PI.toFloat()))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color2, Color.Transparent),
            center = Offset(x2, y2),
            radius = canvasWidth * 0.8f
        ),
        center = Offset(x2, y2),
        radius = canvasWidth * 0.8f
    )

    // Blob 3 (Top Right)
    val x3 = canvasWidth * (0.7f + 0.15f * sin(offset * 3 * Math.PI.toFloat()))
    val y3 = canvasHeight * (0.3f - 0.1f * sin(offset * 2 * Math.PI.toFloat()))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color1.copy(alpha = color1.alpha * 0.7f), Color.Transparent),
            center = Offset(x3, y3),
            radius = canvasWidth * 0.7f
        ),
        center = Offset(x3, y3),
        radius = canvasWidth * 0.7f
    )

    // Blob 4 (Bottom Left)
    val x4 = canvasWidth * (0.3f - 0.1f * sin(offset * 2.2f * Math.PI.toFloat()))
    val y4 = canvasHeight * (0.7f + 0.12f * sin(offset * 1.8f * Math.PI.toFloat()))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color2.copy(alpha = color2.alpha * 0.7f), Color.Transparent),
            center = Offset(x4, y4),
            radius = canvasWidth * 0.6f
        ),
        center = Offset(x4, y4),
        radius = canvasWidth * 0.6f
    )
}

private fun DrawScope.drawDottedGrid(color: Color) {
    val dotSpacing = 24.dp.toPx()
    val dotRadius = 1.dp.toPx()

    val rows = (size.height / dotSpacing).toInt()
    val cols = (size.width / dotSpacing).toInt()

    for (r in 0..rows) {
        for (c in 0..cols) {
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(c * dotSpacing, r * dotSpacing)
            )
        }
    }
}

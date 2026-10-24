package com.example.mobileapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * Executive branded background featuring an outer orange frame/border all around the edges,
 * warm cream inner canvas, soft top orange gradient, and smooth organic layered wave curves at the bottom.
 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    // 1. Outer full-screen orange border frame all around the edges
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF97316), // Top bright warm orange
                        Color(0xFFEA580C)  // Bottom rich brand orange
                    )
                )
            )
            .padding(10.dp) // The visible border thickness around the entire screen
            .clip(RoundedCornerShape(28.dp)) // Smooth rounded screen border corners
            .background(Color(0xFFFAF7F2)) // Inner screen cream background
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Top subtle warm aura inside the framed screen
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEA580C).copy(alpha = 0.08f),
                        Color(0xFFFED7AA).copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = height * 0.35f
                )
            )

            // Wave Layer 1: Soft warm peach background wave
            val path1 = Path().apply {
                moveTo(0f, height * 0.82f)
                cubicTo(
                    width * 0.32f, height * 0.76f,
                    width * 0.68f, height * 0.88f,
                    width, height * 0.80f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(path1, color = Color(0xFFFFEDD5).copy(alpha = 0.90f))

            // Wave Layer 2: Middle warm apricot wave
            val path2 = Path().apply {
                moveTo(0f, height * 0.88f)
                cubicTo(
                    width * 0.30f, height * 0.94f,
                    width * 0.70f, height * 0.83f,
                    width, height * 0.89f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(path2, color = Color(0xFFFDBA74).copy(alpha = 0.85f))

            // Wave Layer 3: Foreground vibrant rich brand orange wave
            val path3 = Path().apply {
                moveTo(0f, height * 0.93f)
                cubicTo(
                    width * 0.38f, height * 0.89f,
                    width * 0.65f, height * 0.97f,
                    width, height * 0.92f
                )
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(path3, color = Color(0xFFEA580C))
        }

        // Inner framed content
        content()
    }
}

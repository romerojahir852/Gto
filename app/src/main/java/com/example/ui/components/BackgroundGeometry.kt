package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp

@Composable
fun BackgroundGeometry(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")

    val blue = Color(0xFF3B82F6)
    val violet = Color(0xFF8B5CF6)
    val pink = Color(0xFFEC4899)

    val configs = listOf(
        PulseConfig(0.08f, 0.0f, 0.0f, 8000L, 0),
        PulseConfig(0.21f, 0.0f, 0.0f, 11000L, 2000),
        PulseConfig(0.33f, 0.0f, 0.0f, 7000L, 4000),
        PulseConfig(0.46f, 0.0f, 0.0f, 9000L, 1000),
        PulseConfig(0.58f, 0.0f, 0.0f, 6000L, 3000),
        PulseConfig(0.71f, 0.0f, 0.0f, 10000L, 5000),
        PulseConfig(0.83f, 0.0f, 0.0f, 14000L, 0),
        PulseConfig(0.96f, 0.0f, 0.0f, 16000L, 3000),
        PulseConfig(0.15f, 0.0f, 0.0f, 7000L, 2000),
    )

    val animatedPulses = configs.mapIndexed { index, cfg ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = cfg.duration.toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(cfg.delay)
            ),
            label = "p$index"
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val gridColor = Color(0xFFE5E5E5).copy(alpha = 0.05f)
        for (i in 1..12) {
            val x = size.width * i / 13f
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.5.dp.toPx()
            )
        }
        for (i in 1..16) {
            val y = size.height * i / 17f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        configs.forEachIndexed { index, cfg ->
            val progress = animatedPulses[index].value

            val x = size.width * cfg.xRel
            val dashOn = 150f
            val dashOff = 2850f
            val phase = -progress * (dashOn + dashOff)

            val pulseColor = lerpPulseColor(progress, blue, violet, pink)

            drawLine(
                color = pulseColor.copy(alpha = 0.55f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(dashOn, dashOff),
                    phase = phase
                )
            )
        }
    }
}

private data class PulseConfig(
    val xRel: Float,
    val yRel: Float,
    val rRel: Float,
    val duration: Long,
    val delay: Int,
)

private fun lerpPulseColor(progress: Float, blue: Color, violet: Color, pink: Color): Color {
    return when {
        progress < 0.33f -> {
            val t = progress / 0.33f
            Color(
                red = blue.red + (violet.red - blue.red) * t,
                green = blue.green + (violet.green - blue.green) * t,
                blue = blue.blue + (violet.blue - blue.blue) * t,
                alpha = 1f
            )
        }
        progress < 0.66f -> {
            val t = (progress - 0.33f) / 0.33f
            Color(
                red = violet.red + (pink.red - violet.red) * t,
                green = violet.green + (pink.green - violet.green) * t,
                blue = violet.blue + (pink.blue - violet.blue) * t,
                alpha = 1f
            )
        }
        else -> {
            val t = (progress - 0.66f) / 0.34f
            Color(
                red = pink.red + (blue.red - pink.red) * t,
                green = pink.green + (blue.green - pink.green) * t,
                blue = pink.blue + (blue.blue - pink.blue) * t,
                alpha = 1f
            )
        }
    }
}

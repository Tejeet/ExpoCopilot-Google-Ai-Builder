package com.tejeet.carddrop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tejeet.carddrop.ui.theme.AppTheme

/**
 * Diagonal light sweep drawn over the content while the on-device AI is analysing a card.
 * [cornerRadius] should match the shape of whatever it's applied to, so the sweep stays inside it.
 */
fun Modifier.aiShimmer(active: Boolean, cornerRadius: Dp = 0.dp): Modifier =
    if (!active) this else composed {
        val tint = AppTheme.colors.brand.accent
        val transition = rememberInfiniteTransition(label = "aiShimmer")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1400, easing = LinearEasing)),
            label = "aiShimmerSweep"
        )
        drawWithContent {
            drawContent()
            val band = size.width * 0.5f
            val centre = -band + (size.width + band * 2) * progress
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        tint.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.45f),
                        tint.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    start = Offset(centre - band, 0f),
                    end = Offset(centre + band, size.height)
                ),
                cornerRadius = CornerRadius(cornerRadius.toPx())
            )
        }
    }

/** Pulsing, slowly turning sparkle with an "AI analyzing" message. */
@Composable
fun AiAnalyzingLabel(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aiLabel")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
        label = "aiLabelPulse"
    )
    val turn by transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "aiLabelTurn"
    )
    val tone = AppTheme.colors.ai
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = tone.content,
            modifier = Modifier.size(18.dp).alpha(pulse).rotate(turn)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tone.content
        )
    }
}

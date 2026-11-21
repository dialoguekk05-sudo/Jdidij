package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisCobalt
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisTeal
import com.example.voice.VoiceState
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun JarvisArcOrb(
    voiceState: VoiceState,
    isThinking: Boolean,
    rmsDb: Float,
    lowRamMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp
) {
    val transition = rememberInfiniteTransition(label = "jarvis_orb_transition")

    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isThinking -> 2400
                    voiceState == VoiceState.LISTENING -> 4200
                    voiceState == VoiceState.SPEAKING -> 3200
                    else -> 9000
                },
                easing = LinearEasing
            )
        ),
        label = "outer_angle"
    )

    val innerAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isThinking) 1800 else 6500,
                easing = LinearEasing
            )
        ),
        label = "inner_angle"
    )

    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val animatedRms by animateFloatAsState(
        targetValue = if (voiceState == VoiceState.LISTENING) rmsDb else 0f,
        animationSpec = spring(),
        label = "rms_spring"
    )

    val primaryGlow = when {
        isThinking -> JarvisTeal
        voiceState == VoiceState.LISTENING -> JarvisCyan
        voiceState == VoiceState.SPEAKING -> JarvisCobalt
        voiceState == VoiceState.WAKE_LISTENING -> Color(0xFF38BDF8)
        else -> JarvisCyan
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = JarvisCyan),
                onClick = onClick
            )
            .testTag("jarvis_orb_mic_button")
            .semantics {
                contentDescription = when (voiceState) {
                    VoiceState.LISTENING -> "Listening. Tap to stop voice input."
                    VoiceState.SPEAKING -> "JARVIS speaking. Tap to stop."
                    VoiceState.WAKE_LISTENING -> "Hey JARVIS wake word active. Tap to speak now."
                    VoiceState.IDLE -> "Tap to speak with JARVIS AI"
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val radius = minOf(w, h) / 2f
            val center = Offset(w / 2f, h / 2f)
            val dynamicScale = 1f + (animatedRms * 0.14f)

            // Ambient holographic radial glow
            if (!lowRamMode) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.28f),
                            JarvisCobalt.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * pulse
                    ),
                    radius = radius,
                    center = center
                )
            }

            // Outer segmented arc ring
            val outerStroke = radius * 0.045f
            val outerInset = radius * 0.14f
            val outerRectSize = Size((radius - outerInset) * 2f, (radius - outerInset) * 2f)
            val outerTopLeft = Offset(center.x - outerRectSize.width / 2f, center.y - outerRectSize.height / 2f)

            for (segment in 0 until 4) {
                drawArc(
                    color = primaryGlow.copy(alpha = if (segment % 2 == 0) 0.85f else 0.35f),
                    startAngle = outerAngle + (segment * 90f),
                    sweepAngle = 62f,
                    useCenter = false,
                    topLeft = outerTopLeft,
                    size = outerRectSize,
                    style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                )
            }

            // Middle counter-rotating ring
            val midInset = radius * 0.28f
            val midRectSize = Size((radius - midInset) * 2f, (radius - midInset) * 2f)
            val midTopLeft = Offset(center.x - midRectSize.width / 2f, center.y - midRectSize.height / 2f)

            for (segment in 0 until 3) {
                drawArc(
                    color = JarvisCobalt.copy(alpha = 0.9f),
                    startAngle = innerAngle + (segment * 120f),
                    sweepAngle = 82f,
                    useCenter = false,
                    topLeft = midTopLeft,
                    size = midRectSize,
                    style = Stroke(width = outerStroke * 0.8f, cap = StrokeCap.Round)
                )
            }

            // Inner glowing core circle
            val coreRadius = radius * 0.34f * dynamicScale * (if (voiceState == VoiceState.SPEAKING) pulse else 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = 0.95f),
                        JarvisCobalt.copy(alpha = 0.7f),
                        primaryGlow.copy(alpha = 0.18f)
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = coreRadius * 0.96f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        Icon(
            imageVector = when {
                voiceState == VoiceState.LISTENING -> Icons.Default.Stop
                voiceState == VoiceState.SPEAKING -> Icons.Default.GraphicEq
                else -> Icons.Default.Mic
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
fun JarvisVoiceWaveform(
    voiceState: VoiceState,
    rmsDb: Float,
    isThinking: Boolean,
    lowRamMode: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = if (lowRamMode) 14 else 24
    val transition = rememberInfiniteTransition(label = "waveform_transition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    val amplitudeTarget = when {
        voiceState == VoiceState.LISTENING -> (0.28f + rmsDb * 0.72f).coerceIn(0.25f, 1f)
        voiceState == VoiceState.SPEAKING -> 0.75f
        isThinking -> 0.52f
        voiceState == VoiceState.WAKE_LISTENING -> 0.22f
        else -> 0.12f
    }

    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitudeTarget,
        animationSpec = spring(),
        label = "wave_amp"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val maxHeight = size.height
            val slotWidth = totalWidth / barCount
            val barWidth = (slotWidth * 0.48f).coerceAtLeast(3.dp.toPx())

            for (i in 0 until barCount) {
                val normPos = i.toFloat() / (barCount - 1).coerceAtLeast(1)
                val envelope = sin(normPos * PI).toFloat().coerceAtLeast(0.18f)
                val wave = 0.5f + 0.5f * sin(phase + i * 0.55f)
                val barHeight = (maxHeight * animatedAmplitude * envelope * (0.45f + 0.55f * wave))
                    .coerceIn(4.dp.toPx(), maxHeight)

                val x = i * slotWidth + (slotWidth - barWidth) / 2f
                val y = (maxHeight - barHeight) / 2f

                val barColor = if (i % 2 == 0) JarvisCyan else JarvisCobalt
                drawRoundRect(
                    color = barColor.copy(alpha = 0.85f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}

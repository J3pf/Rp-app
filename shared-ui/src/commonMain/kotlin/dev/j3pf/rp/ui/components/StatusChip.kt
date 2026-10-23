package dev.j3pf.rp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpMotion
import dev.j3pf.rp.ui.util.relativeTime

@Composable
fun ColorDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 10,
) {
    Box(modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun ColorDot(
    color: Color,
    size: Int,
) {
    ColorDot(color = color, modifier = Modifier, size = size)
}

@Composable
fun StatusChip(
    agent: AgentSummary,
    modifier: Modifier = Modifier,
) {
    val (text, targetColor) =
        when {
            !agent.online -> "Desconectado ${relativeTime(agent.lastSeenAt)}".trim() to MaterialTheme.colorScheme.outline
            agent.openPromptCount > 0 || agent.status == AgentStatus.WAITING_INPUT -> "Esperando respuesta" to RpColors.warning
            agent.status == AgentStatus.WORKING -> "Trabajando" to MaterialTheme.colorScheme.primary
            agent.status == AgentStatus.COMPACTING -> "Compactando" to MaterialTheme.colorScheme.primary
            agent.status == AgentStatus.ERROR -> "Error" to RpColors.danger
            else -> "Inactivo" to RpColors.success
        }

    val reducedMotion = LocalReducedMotion.current
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = RpMotion.adaptiveSpec(reducedMotion, tween(durationMillis = 300)),
        label = "statusColor",
    )

    val isWorking = (agent.status == AgentStatus.WORKING || agent.status == AgentStatus.COMPACTING) && agent.online
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulseTransition")
    val pulseScale by if (isWorking && !reducedMotion) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 2.2f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "pulseScale",
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }
    val pulseAlpha by if (isWorking && !reducedMotion) {
        infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0.0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "pulseAlpha",
        )
    } else {
        remember { mutableFloatStateOf(0.0f) }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = animatedColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, animatedColor.copy(alpha = 0.25f)),
        contentColor = animatedColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
                if (isWorking && !reducedMotion && pulseAlpha > 0f) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }.clip(CircleShape)
                            .background(animatedColor),
                    )
                }
                ColorDot(animatedColor, size = 6)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

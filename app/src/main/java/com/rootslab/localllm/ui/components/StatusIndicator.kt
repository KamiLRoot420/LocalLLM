package com.rootslab.localllm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rootslab.localllm.ui.model.ServerStatus

/**
 * Traffic-light style status indicator:
 * green = ready, yellow = loading/processing, red = stopped/error.
 */
@Composable
fun StatusIndicator(
    status: ServerStatus,
    modifier: Modifier = Modifier
) {
    val targetColor = when (status) {
        ServerStatus.READY -> Color(0xFF00C853)
        ServerStatus.LOADING -> Color(0xFFFFC107)
        ServerStatus.ERROR -> Color(0xFFFF5252)
        ServerStatus.STOPPED -> Color(0xFFFF5252)
    }

    val color by animateColorAsState(targetValue = targetColor, label = "statusColor")

    val transition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == ServerStatus.LOADING) 0.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusAlpha"
    )

    Box(
        modifier = modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .alpha(pulse)
                .clip(CircleShape)
                .background(color)
        )
    }
}

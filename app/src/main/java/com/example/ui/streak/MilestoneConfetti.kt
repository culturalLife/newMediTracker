package com.example.ui.streak

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen celebration overlay shown when the user crosses a streak milestone.
 *
 * Renders a centered congratulatory card on top of a Compose-only confetti shower
 * (no Lottie / no extra deps). Auto-dismisses after a short delay; the user can
 * also tap "Awesome!" to dismiss immediately.
 */
@Composable
fun MilestoneConfetti(
    milestone: Milestone,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val particles = remember(milestone) { generateParticles(count = 80) }
    var elapsedMs by remember(milestone) { mutableStateOf(0L) }

    LaunchedEffect(milestone) {
        var lastTime = 0L
        val totalMs = 4500L
        while (elapsedMs < totalMs) {
            withFrameNanos { now ->
                if (lastTime != 0L) elapsedMs += (now - lastTime) / 1_000_000
                lastTime = now
            }
        }
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss)
            .testTag("milestone_confetti"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tSec = elapsedMs / 1000f
            particles.forEach { p ->
                val gravityY = 220f * tSec * tSec / 2f
                val x = p.startX * size.width + p.vx * tSec * size.width / 4f
                val y = p.startY * size.height + p.vy * tSec * size.height / 3f + gravityY
                val rotation = p.rotationSpeed * tSec
                val alpha = (1f - (elapsedMs.toFloat() / 4500f)).coerceIn(0f, 1f)
                rotate(degrees = rotation, pivot = Offset(x, y)) {
                    drawRoundRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.size / 2, y - p.size / 2),
                        size = Size(p.size, p.size * 0.6f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = milestone.emoji, fontSize = 56.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = milestone.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${milestone.days}-day streak",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = milestone.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("milestone_dismiss")
            ) {
                Text("Awesome!", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class Particle(
    val startX: Float,
    val startY: Float,
    val vx: Float,
    val vy: Float,
    val rotationSpeed: Float,
    val size: Float,
    val color: Color
)

private fun generateParticles(count: Int): List<Particle> {
    val palette = listOf(
        Color(0xFF00897B), Color(0xFF4DB6AC), Color(0xFFFFC107),
        Color(0xFFFF7043), Color(0xFF7E57C2), Color(0xFF42A5F5),
        Color(0xFFEC407A)
    )
    val random = Random(System.currentTimeMillis())
    return List(count) {
        val angle = random.nextFloat() * Math.PI.toFloat() * 2f
        val speed = 0.6f + random.nextFloat() * 1.4f
        Particle(
            startX = 0.3f + random.nextFloat() * 0.4f,
            startY = 0.3f + random.nextFloat() * 0.2f,
            vx = cos(angle) * speed,
            // Bias upward at t=0 so the burst rises before gravity pulls the pieces down.
            vy = sin(angle) * speed - 1.2f,
            rotationSpeed = (random.nextFloat() - 0.5f) * 720f,
            size = 12f + random.nextFloat() * 10f,
            color = palette[random.nextInt(palette.size)]
        )
    }
}

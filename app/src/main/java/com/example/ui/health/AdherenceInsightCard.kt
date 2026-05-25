package com.example.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.model.AdherenceInsight

/**
 * A soft, supportive Impact Card surfaced on Home when the user has missed doses.
 *
 * Visual treatment is deliberately calm — soft amber background, no warning iconography,
 * no fear language. Renders the AI-generated supportive note, generic compensation
 * tip, and a fallback "consult your doctor" message when the model isn't confident.
 *
 * Renders nothing when state is Idle so it doesn't clutter the Home tab for users
 * with perfect adherence.
 */
@Composable
fun AdherenceInsightCard(
    state: HealthInsightsViewModel.InsightState,
    isConfigured: Boolean,
    onLoad: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isConfigured) return
    if (state is HealthInsightsViewModel.InsightState.Idle) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SoftAmberBg)
            .testTag("adherence_insight_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AccentAmber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ADHERENCE INSIGHT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = AccentAmber,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("dismiss_insight")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = AccentAmber.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                is HealthInsightsViewModel.InsightState.Loading -> LoadingBlock()
                is HealthInsightsViewModel.InsightState.Success -> SuccessBlock(insight = state.insight)
                is HealthInsightsViewModel.InsightState.Error -> ErrorBlock(state.message, onLoad)
                else -> Unit
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = AccentAmber
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Looking at your recent doses…",
            style = MaterialTheme.typography.bodySmall,
            color = AccentAmberDeep
        )
    }
}

@Composable
private fun SuccessBlock(insight: AdherenceInsight) {
    if (!insight.supportiveNote.isNullOrBlank()) {
        Text(
            text = insight.supportiveNote,
            style = MaterialTheme.typography.bodyMedium,
            color = AccentAmberDeep
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (!insight.compensationTip.isNullOrBlank()) {
        SectionPill(label = "Catching up safely", color = AccentAmber)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = insight.compensationTip,
            style = MaterialTheme.typography.bodySmall,
            color = AccentAmberDeep
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (!insight.consultDoctorNote.isNullOrBlank()) {
        SectionPill(label = "Talk to your doctor", color = Color(0xFFC62828))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = insight.consultDoctorNote,
            style = MaterialTheme.typography.bodySmall,
            color = AccentAmberDeep
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (!insight.encouragement.isNullOrBlank()) {
        Text(
            text = "✨  ${insight.encouragement}",
            style = MaterialTheme.typography.labelSmall,
            color = AccentAmberDeep.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    TextButton(onClick = onRetry) {
        Text("Try again")
    }
}

@Composable
private fun SectionPill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

private val SoftAmberBg = Color(0xFFFFF8E1)
private val AccentAmber = Color(0xFFB57700)
private val AccentAmberDeep = Color(0xFF6B4A00)

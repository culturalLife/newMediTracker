package com.example.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.ai.model.SafetyAssessment

/**
 * Visual card that shows the bio-safety assessment for the user's medicine list.
 *
 * Surfaces four distinct states:
 *   - Idle  → CTA to "Run Safety Check"
 *   - Loading → spinner while the AI scans
 *   - Success → color-coded badge + summary + concerns list
 *   - Error → small inline error with retry
 *
 * The card is fully self-contained: it renders nothing if there are no medicines yet
 * and shows a soft "configure your key" hint if the API key is missing.
 */
@Composable
fun BiosafetyBadge(
    state: HealthInsightsViewModel.SafetyState,
    medicineCount: Int,
    isConfigured: Boolean,
    onRunCheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (medicineCount == 0) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("biosafety_badge"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Header()
            Spacer(modifier = Modifier.height(10.dp))

            when {
                !isConfigured -> NotConfiguredHint()
                state is HealthInsightsViewModel.SafetyState.Idle -> IdleState(
                    medicineCount = medicineCount,
                    onRunCheck = onRunCheck
                )
                state is HealthInsightsViewModel.SafetyState.Loading -> LoadingState()
                state is HealthInsightsViewModel.SafetyState.Success -> SuccessState(
                    assessment = state.assessment,
                    onRecheck = onRunCheck
                )
                state is HealthInsightsViewModel.SafetyState.Error -> ErrorState(
                    message = state.message,
                    onRetry = onRunCheck
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "BIO-SAFETY CHECK",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun IdleState(medicineCount: Int, onRunCheck: () -> Unit) {
    Text(
        text = "Scan your $medicineCount active medicines for potential interactions and contraindications.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    )
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onRunCheck,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("run_safety_check"),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text("Run Safety Check", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Scanning medicines for interactions…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SuccessState(
    assessment: SafetyAssessment,
    onRecheck: () -> Unit
) {
    val palette = LevelPalette.forLevel(assessment.normalizedLevel())

    // Big colored level pill
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.tint)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(palette.color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = palette.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = palette.color
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = palette.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = palette.color.copy(alpha = 0.85f)
        )
    }

    if (!assessment.summary.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = assessment.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    val concerns = assessment.concerns.orEmpty().take(6)
    if (concerns.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Concerns",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        concerns.forEach { concern ->
            ConcernRow(
                medicineA = concern.medicineA,
                medicineB = concern.medicineB,
                severity = concern.severity,
                description = concern.description
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Always confirm with your pharmacist or doctor before changing any medication.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline
    )

    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onRecheck,
        modifier = Modifier.testTag("rerun_safety_check")
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Re-scan", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ConcernRow(
    medicineA: String?,
    medicineB: String?,
    severity: String?,
    description: String?
) {
    val severityColor = when (severity?.lowercase()) {
        "high" -> Color(0xFFC62828)
        "moderate" -> Color(0xFFB57700)
        "low" -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.outline
    }
    val pairLabel = when {
        !medicineA.isNullOrBlank() && !medicineB.isNullOrBlank() -> "$medicineA + $medicineB"
        !medicineA.isNullOrBlank() -> medicineA
        else -> "Concern"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = severityColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(severityColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pairLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!severity.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = severity.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = severityColor
                    )
                }
            }
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(modifier = Modifier.height(6.dp))
    TextButton(onClick = onRetry) {
        Text("Try again")
    }
}

@Composable
private fun NotConfiguredHint() {
    Text(
        text = "Add MISTRAL_API_KEY to your local .env to enable safety scans.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

private data class LevelPalette(
    val color: Color,
    val tint: Color,
    val label: String,
    val subtitle: String
) {
    companion object {
        fun forLevel(level: SafetyAssessment.Level): LevelPalette = when (level) {
            SafetyAssessment.Level.GREEN -> LevelPalette(
                color = Color(0xFF2E7D32),
                tint = Color(0xFFE8F5E9),
                label = "GREEN",
                subtitle = "No major concerns detected"
            )
            SafetyAssessment.Level.YELLOW -> LevelPalette(
                color = Color(0xFFB57700),
                tint = Color(0xFFFFF8E1),
                label = "YELLOW",
                subtitle = "Caution — review with pharmacist"
            )
            SafetyAssessment.Level.RED -> LevelPalette(
                color = Color(0xFFC62828),
                tint = Color(0xFFFFEBEE),
                label = "RED",
                subtitle = "High-severity concern — consult your doctor"
            )
            SafetyAssessment.Level.UNKNOWN -> LevelPalette(
                color = Color(0xFF6A6A6A),
                tint = Color(0xFFF5F5F5),
                label = "UNKNOWN",
                subtitle = "Unable to assess — confirm with pharmacist"
            )
        }
    }
}

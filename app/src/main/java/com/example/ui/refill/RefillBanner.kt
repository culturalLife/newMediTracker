package com.example.ui.refill

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.refill.RefillCalculator
import java.time.format.DateTimeFormatter

/**
 * Compact top-of-Home banner that shows the most-urgent low-stock medicine when
 * any active medicine has refill tracking enabled and is at or below the threshold.
 *
 * Renders nothing when the [forecasts] list is empty so it doesn't clutter the
 * Home tab for users with healthy supply or who haven't enabled refill tracking.
 */
@Composable
fun RefillBanner(
    forecasts: List<RefillCalculator.Forecast>,
    modifier: Modifier = Modifier
) {
    val urgent = forecasts
        .filter { it.isLowStock && it.daysRemaining != null }
        .sortedBy { it.daysRemaining }
    if (urgent.isEmpty()) return

    val first = urgent.first()
    val rest = urgent.drop(1)
    val dateFmt = DateTimeFormatter.ofPattern("MMM d")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SoftAmberBg)
            .testTag("refill_banner")
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AccentAmber.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "💊", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Refill ${first.medicine.name} soon",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = AccentAmberDeep
                    )
                    val days = first.daysRemaining ?: 0
                    val outOn = first.runOutDate?.format(dateFmt) ?: "—"
                    val daysLabel = if (days == 0) "out today" else "$days days left"
                    Text(
                        text = "$daysLabel \u2022 runs out $outOn",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentAmberDeep.copy(alpha = 0.85f)
                    )
                }
            }
            if (rest.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "+${rest.size} more low-stock " +
                        if (rest.size == 1) "medicine" else "medicines",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentAmberDeep.copy(alpha = 0.75f)
                )
            }
        }
    }
}

private val SoftAmberBg = Color(0xFFFFF3D6)
private val AccentAmber = Color(0xFFB57700)
private val AccentAmberDeep = Color(0xFF6B4A00)

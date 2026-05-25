package com.example.ui.chat.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.model.MedicineInfo

/**
 * Rich, structured info card rendered when the assistant returns a parsed [MedicineInfo].
 * Lays out clearly-labeled sections (Purpose, Dosage, Food, Warnings, Interactions, Side Effects)
 * with color-coded section pills. Empty sections are omitted.
 */
@Composable
fun MedicineInfoCardUi(
    info: MedicineInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!info.medicineName.isNullOrBlank()) {
                Text(
                    text = info.medicineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            InfoSection(
                emoji = "💊",
                label = "Purpose",
                accent = SectionAccent.Teal,
                text = info.purpose
            )
            InfoSection(
                emoji = "📏",
                label = "Common Dosage",
                accent = SectionAccent.Blue,
                text = info.commonDosage
            )
            InfoSection(
                emoji = "🍽️",
                label = "Food Advice",
                accent = SectionAccent.Green,
                text = info.foodAdvice
            )
            InfoBulletSection(
                emoji = "⚠️",
                label = "Warnings",
                accent = SectionAccent.Amber,
                bullets = info.warnings
            )
            InfoBulletSection(
                emoji = "🔗",
                label = "Interactions",
                accent = SectionAccent.Red,
                bullets = info.interactions
            )
            InfoBulletSection(
                emoji = "🩺",
                label = "Common Side Effects",
                accent = SectionAccent.Purple,
                bullets = info.sideEffects
            )
        }
    }
}

private enum class SectionAccent(val color: Color, val tint: Color) {
    Teal(Color(0xFF00897B), Color(0xFFE0F2F1)),
    Blue(Color(0xFF1976D2), Color(0xFFE3F2FD)),
    Green(Color(0xFF2E7D32), Color(0xFFE8F5E9)),
    Amber(Color(0xFFB57700), Color(0xFFFFF8E1)),
    Red(Color(0xFFC62828), Color(0xFFFFEBEE)),
    Purple(Color(0xFF6A1B9A), Color(0xFFF3E5F5))
}

@Composable
private fun InfoSection(
    emoji: String,
    label: String,
    accent: SectionAccent,
    text: String?
) {
    if (text.isNullOrBlank()) return
    SectionHeader(emoji = emoji, label = label, accent = accent)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun InfoBulletSection(
    emoji: String,
    label: String,
    accent: SectionAccent,
    bullets: List<String>?
) {
    if (bullets.isNullOrEmpty()) return
    SectionHeader(emoji = emoji, label = label, accent = accent)
    Spacer(modifier = Modifier.height(4.dp))
    bullets.take(4).forEach { bullet ->
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent.color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = bullet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun SectionHeader(emoji: String, label: String, accent: SectionAccent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.tint)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = emoji, style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = accent.color
        )
    }
}

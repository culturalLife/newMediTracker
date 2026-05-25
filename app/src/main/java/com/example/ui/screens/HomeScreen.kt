package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DoseLog
import com.example.data.model.Profile
import com.example.ui.viewmodel.MainViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val profilesList by viewModel.profilesList.collectAsState()
    val doseLogs by viewModel.doseLogsList.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }
    var showCreateProfileInHome by remember { mutableStateOf(false) }

    val formatterMonthHeader = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    // Retrieve active horizontal date items for last 7 days (today is the last item, or clickable)
    val datesStrip = remember {
        val today = LocalDate.now()
        (6 downTo 0).map { today.minusDays(it.toLong()) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar row details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = selectedDate.format(formatterMonthHeader),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (selectedDate.isEqual(LocalDate.now())) "Today" else "Past log review",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Profile Toggle Switcher circle
                selectedProfile?.let { profile ->
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PROFILE_COLORS[profile.avatarColor % PROFILE_COLORS.size])
                            .clickable { showProfileDialog = true }
                            .testTag("home_profile_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.name.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Horizontal Date strip row list
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                items(datesStrip) { date ->
                    val isSelected = date.isEqual(selectedDate)
                    val isToday = date.isEqual(LocalDate.now())

                    val dayOfWeekStr = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    val dayOfMonthStr = date.dayOfMonth.toString()

                    Card(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(50.dp)
                            .clickable { viewModel.selectDate(date) }
                            .testTag("date_strip_${date.dayOfMonth}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else if (isToday) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = dayOfWeekStr,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dayOfMonthStr,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Daily Progress Bar Section Card
            val totalMedsToday = doseLogs.size
            val takenMedsToday = doseLogs.count { it.status == "Taken" }
            val completionRatio = if (totalMedsToday > 0) takenMedsToday.toFloat() / totalMedsToday else 0f
            val animateProgress by animateFloatAsState(targetValue = completionRatio, label = "progress")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Progress",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$takenMedsToday of $totalMedsToday Taken",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { animateProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = Color(0xFF4CAF50),
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                }
            }

            // Today's list divided by time of day
            if (doseLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("🍃", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "All clear!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No medications scheduled for this profile on this date. Use the Medicines page to register routines.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val groupedLogs = remember(doseLogs) {
                    groupDoseLogsTimeOfDay(doseLogs)
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    groupedLogs.forEach { (timeHeader, logs) ->
                        item {
                            Text(
                                text = timeHeader,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }

                        items(logs) { log ->
                            DoseLogCard(
                                log = log,
                                onTake = { viewModel.markDoseStatus(log, "Taken") },
                                onSkip = { viewModel.markDoseStatus(log, "Skipped") },
                                onReset = { viewModel.markDoseStatus(log, "Pending") }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

        // Dialog Switcher profile modal selection
        if (showProfileDialog) {
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                title = { Text("Switch Tracking Profile") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Profiles selection row/columns list
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                            items(profilesList) { profile ->
                                val active = profile.id == selectedProfile?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable {
                                            viewModel.selectProfile(profile)
                                            showProfileDialog = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(PROFILE_COLORS[profile.avatarColor % PROFILE_COLORS.size]),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (active) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        // Option to delete
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteProfile(profile)
                                                if (profile.id == selectedProfile?.id) {
                                                    showProfileDialog = false
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Profile",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                showCreateProfileInHome = true
                                showProfileDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create New Family Member")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Sub-dialog modal sheet to add profiles
        if (showCreateProfileInHome) {
            var newName by remember { mutableStateOf("") }
            var newColorIndex by remember { mutableStateOf(0) }

            AlertDialog(
                onDismissRequest = { showCreateProfileInHome = false },
                title = { Text("New Tracking Profile") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Color Highlight Theme",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Row of profile colors
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            PROFILE_COLORS.forEachIndexed { index, color ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { newColorIndex = index }
                                        .border(
                                            width = if (newColorIndex == index) 2.dp else 0.dp,
                                            color = if (newColorIndex == index) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.createProfile(newName.trim(), newColorIndex)
                                showCreateProfileInHome = false
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateProfileInHome = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Group logs based on hour ranges cleanly
 */
private fun groupDoseLogsTimeOfDay(logs: List<DoseLog>): Map<String, List<DoseLog>> {
    val morning = mutableListOf<DoseLog>()
    val afternoon = mutableListOf<DoseLog>()
    val evening = mutableListOf<DoseLog>()
    val night = mutableListOf<DoseLog>()

    for (log in logs) {
        try {
            val parsedTime = LocalTime.parse(log.scheduledTime)
            val hour = parsedTime.hour
            when (hour) {
                in 5..11 -> morning.add(log)
                in 12..16 -> afternoon.add(log)
                in 17..20 -> evening.add(log)
                else -> night.add(log)
            }
        } catch (e: Exception) {
            morning.add(log) // Fallback
        }
    }

    val map = mutableMapOf<String, List<DoseLog>>()
    if (morning.isNotEmpty()) map["🌅 Morning (05:00 AM - 12:00 PM)"] = morning.sortedBy { it.scheduledTime }
    if (afternoon.isNotEmpty()) map["☀️ Afternoon (12:00 PM - 05:00 PM)"] = afternoon.sortedBy { it.scheduledTime }
    if (evening.isNotEmpty()) map["🌆 Evening (05:00 PM - 09:00 PM)"] = evening.sortedBy { it.scheduledTime }
    if (night.isNotEmpty()) map["🌙 Night (09:00 PM - 05:00 AM)"] = night.sortedBy { it.scheduledTime }
    return map
}

@Composable
fun DoseLogCard(
    log: DoseLog,
    onTake: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatterTime = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val formattedScheduled = remember(log.scheduledTime) {
        val parsed = LocalTime.parse(log.scheduledTime)
        parsed.format(formatterTime)
    }

    val stateColor = when (log.status) {
        "Taken" -> Color(0xFF4CAF50)
        "Skipped" -> Color(0xFF9E9E9E)
        "Missed" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dose_card_${log.medicineId}_${log.scheduledTime}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (log.status == "Taken") BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual vertical strip color highlight for identification
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(stateColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.medicineName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• $formattedScheduled",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = stateColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = log.dosage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Timestamp display
                if (log.status == "Taken" && log.timestamp != null) {
                    val timeTaken = Instant.ofEpochMilli(log.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .format(formatterTime)

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Taken at $timeTaken",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4CAF50)
                    )
                } else if (log.status == "Skipped") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Intentionally Skipped",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray
                    )
                } else if (log.status == "Missed") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Missed this schedule",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF44336)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Action interactive buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (log.status == "Pending" || log.status == "Missed") {
                    // Skip button option
                    IconButton(
                        onClick = onSkip,
                        modifier = Modifier.testTag("skip_dose_button_${log.medicineId}")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Skip dose",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Marking check checkbox button
                    FilledIconButton(
                        onClick = onTake,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("mark_taken_button_${log.medicineId}")
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Mark Taken",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    // Undo status to Pending
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.testTag("undo_dose_button_${log.medicineId}")
                    ) {
                        Text(
                            text = "Undo",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

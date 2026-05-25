package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Medicine
import com.example.ui.chat.AskAiChip
import com.example.ui.health.BiosafetyBadge
import com.example.ui.health.HealthInsightsViewModel
import com.example.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicinesScreen(
    viewModel: MainViewModel,
    insightsVM: HealthInsightsViewModel,
    onAskAi: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val medicinesList by viewModel.medicinesList.collectAsState()
    val safetyState by insightsVM.safety.collectAsState()
    val context = LocalContext.current

    // Whenever the medicine set changes, the previous safety result is stale.
    // We auto-reset back to Idle so the user is prompted to re-scan.
    LaunchedEffect(medicinesList.map { Triple(it.id, it.name, it.dosage) }) {
        if (safetyState is HealthInsightsViewModel.SafetyState.Success ||
            safetyState is HealthInsightsViewModel.SafetyState.Error
        ) {
            insightsVM.resetSafety()
        }
    }

    var showFormDialog by remember { mutableStateOf(false) }
    var editingMedicine by remember { mutableStateOf<Medicine?>(null) }

    // Form inputs state
    var medName by remember { mutableStateOf("") }
    var medDosage by remember { mutableStateOf("") }
    var medFrequency by remember { mutableStateOf("Once Daily") }
    var medTimesList by remember { mutableStateOf(listOf("08:00")) }
    var medStartDate by remember { mutableStateOf(LocalDate.now()) }
    var medEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var medNotes by remember { mutableStateOf("") }
    var medColorTag by remember { mutableStateOf(0) }
    var medRemindersEnabled by remember { mutableStateOf(true) }

    val formatterDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

    // Populate form for editing
    val openEditForm = { med: Medicine ->
        editingMedicine = med
        medName = med.name
        medDosage = med.dosage
        medFrequency = med.frequency
        medTimesList = med.getTimesList()
        medStartDate = LocalDate.ofEpochDay(med.startDate)
        medEndDate = med.endDate?.let { LocalDate.ofEpochDay(it) }
        medNotes = med.notes ?: ""
        medColorTag = med.colorTag
        medRemindersEnabled = med.isReminderEnabled
        showFormDialog = true
    }

    // Reset form for adding new
    val openAddForm = {
        editingMedicine = null
        medName = ""
        medDosage = ""
        medFrequency = "Once Daily"
        medTimesList = listOf("08:00")
        medStartDate = LocalDate.now()
        medEndDate = null
        medNotes = ""
        medColorTag = 0
        medRemindersEnabled = true
        showFormDialog = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Screen Header title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Regulated Medicines",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Medicines List
            if (medicinesList.isEmpty()) {
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
                        Text("💊", fontSize = 54.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Medicines Added",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tapping the '+' FAB down below allows creating tracking routines for this profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    // Bio-safety scanner card pinned to the top of the list. Hidden by
                    // BiosafetyBadge itself when there are no medicines yet.
                    item {
                        BiosafetyBadge(
                            state = safetyState,
                            medicineCount = medicinesList.size,
                            isConfigured = insightsVM.isConfigured,
                            onRunCheck = { insightsVM.runSafetyCheck(medicinesList) },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    items(medicinesList) { med ->
                        MedicineItemCard(
                            medicine = med,
                            onEdit = { openEditForm(med) },
                            onDelete = { viewModel.deleteMedicine(med) },
                            onAskAi = { onAskAi(med.name) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        // Action Floating Action Button
        FloatingActionButton(
            onClick = openAddForm,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_medicine_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Medicine")
        }

        // Unified Add/Edit Medicine Dialog Form overlay
        if (showFormDialog) {
            AlertDialog(
                onDismissRequest = { showFormDialog = false },
                title = {
                    Text(
                        text = if (editingMedicine == null) "Add New Medicine" else "Edit Medicine Routine",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                    ) {
                        item {
                            // Name Input
                            OutlinedTextField(
                                value = medName,
                                onValueChange = { medName = it },
                                label = { Text("Medicine Name") },
                                placeholder = { Text("e.g. Paracetamol, Ibuprofen") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_med_name")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Dosage
                            OutlinedTextField(
                                value = medDosage,
                                onValueChange = { medDosage = it },
                                label = { Text("Dosage Strength") },
                                placeholder = { Text("e.g. 500 mg, 2 drops, 1 table") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_med_dosage")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Frequency dropdown simple selection row
                            Text(
                                text = "Dose Frequency",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val frequencies = listOf("Once Daily", "Twice Daily", "Three Times Daily", "Custom Times")
                                frequencies.forEach { freq ->
                                    val isSel = medFrequency == freq
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                medFrequency = freq
                                                // Adjust initial default times matching the frequency mode
                                                medTimesList = when (freq) {
                                                    "Once Daily" -> listOf("08:00")
                                                    "Twice Daily" -> listOf("08:00", "20:00")
                                                    "Three Times Daily" -> listOf("08:00", "14:00", "20:00")
                                                    else -> listOf("08:00")
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = freq.substringBefore(" Daily"),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Custom Scheduled Times Pickers section
                            Text(
                                text = "Dose Times Schedule",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Column {
                                medTimesList.forEachIndexed { idx, timeStr ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        val parsed = LocalTime.parse(timeStr)
                                        val amPmStr = parsed.format(DateTimeFormatter.ofPattern("h:mm a"))
                                        
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    val picker = TimePickerDialog(
                                                        context,
                                                        { _, hour, min ->
                                                            val formatted = String.format(Locale.US, "%02d:%02d", hour, min)
                                                            val updatedList = medTimesList.toMutableList()
                                                            updatedList[idx] = formatted
                                                            medTimesList = updatedList
                                                        },
                                                        parsed.hour,
                                                        parsed.minute,
                                                        false
                                                    )
                                                    picker.show()
                                                },
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Text(
                                                text = "Dose #${idx + 1}:   $amPmStr",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (medFrequency == "Custom Times") {
                                            // Allow adding or deleting times
                                            IconButton(
                                                onClick = {
                                                    if (medTimesList.size > 1) {
                                                        medTimesList = medTimesList.toMutableList().also { it.removeAt(idx) }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete dose time", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }

                                if (medFrequency == "Custom Times") {
                                    TextButton(
                                        onClick = {
                                            medTimesList = medTimesList.toMutableList().also { it.add("12:00") }
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Dose Time")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Start & End Dates Section Row lists
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Start Date",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                DatePickerDialog(
                                                    context,
                                                    { _, y, m, d ->
                                                        medStartDate = LocalDate.of(y, m + 1, d)
                                                    },
                                                    medStartDate.year,
                                                    medStartDate.monthValue - 1,
                                                    medStartDate.dayOfMonth
                                                ).show()
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = medStartDate.format(formatterDate),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "End Date (Optional)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val currentEnd = medEndDate ?: LocalDate.now()
                                                DatePickerDialog(
                                                    context,
                                                    { _, y, m, d ->
                                                        medEndDate = LocalDate.of(y, m + 1, d)
                                                    },
                                                    currentEnd.year,
                                                    currentEnd.monthValue - 1,
                                                    currentEnd.dayOfMonth
                                                ).apply {
                                                    // Allow clearing end date by dismissing or providing neutral clear button
                                                    setButton(DatePickerDialog.BUTTON_NEUTRAL, "None") { _, _ ->
                                                        medEndDate = null
                                                    }
                                                }.show()
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = medEndDate?.format(formatterDate) ?: "Continuous",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Notes optional field
                            OutlinedTextField(
                                value = medNotes,
                                onValueChange = { medNotes = it },
                                label = { Text("Directions / Notes (Optional)") },
                                placeholder = { Text("e.g. Take after breakfast, with water") },
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Color Identification Palette Picker List row
                            Text(
                                text = "Visual Pill Color Tag",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PROFILE_COLORS.forEachIndexed { index, color ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable { medColorTag = index }
                                            .border(
                                                width = if (medColorTag == index) 3.dp else 0.dp,
                                                color = if (medColorTag == index) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Reminders Enabled toggle switcher
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Setup Push Notifications",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Fires full-screen overlay and smart snooze reminders for scheduled times.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = medRemindersEnabled,
                                    onCheckedChange = { medRemindersEnabled = it }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (medName.isNotBlank() && medDosage.isNotBlank()) {
                                if (editingMedicine == null) {
                                    viewModel.addMedicine(
                                        name = medName.trim(),
                                        dosage = medDosage.trim(),
                                        frequency = medFrequency,
                                        times = medTimesList,
                                        startDate = medStartDate,
                                        endDate = medEndDate,
                                        notes = if (medNotes.isBlank()) null else medNotes.trim(),
                                        colorTag = medColorTag,
                                        isReminderEnabled = medRemindersEnabled
                                    )
                                } else {
                                    viewModel.updateMedicine(
                                        id = editingMedicine!!.id,
                                        name = medName.trim(),
                                        dosage = medDosage.trim(),
                                        frequency = medFrequency,
                                        times = medTimesList,
                                        startDate = medStartDate,
                                        endDate = medEndDate,
                                        notes = if (medNotes.isBlank()) null else medNotes.trim(),
                                        colorTag = medColorTag,
                                        isReminderEnabled = medRemindersEnabled
                                    )
                                }
                                showFormDialog = false
                            }
                        },
                        enabled = medName.isNotBlank() && medDosage.isNotBlank()
                    ) {
                        Text(if (editingMedicine == null) "Add" else "Save Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFormDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun MedicineItemCard(
    medicine: Medicine,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAskAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val formatterTime = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val formattedTimes = remember(medicine.timesCsv) {
        medicine.getTimesList().map {
            val parsed = LocalTime.parse(it)
            parsed.format(formatterTime)
        }
    }

    val tagColor = remember(medicine.colorTag) {
        PROFILE_COLORS[medicine.colorTag % PROFILE_COLORS.size]
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("medicine_item_${medicine.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(tagColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = medicine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit medicine",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete medicine",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Dosage: ${medicine.dosage}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Times:    ${formattedTimes.joinToString(" , ")}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (!medicine.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Notes: ${medicine.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Label continuous duration info
                Text(
                    text = if (medicine.endDate != null) "Until ${LocalDate.ofEpochDay(medicine.endDate).format(DateTimeFormatter.ofPattern("MMM d"))}" else "Continuous",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )

                // Inline "Ask AI about this medicine" entry point
                AskAiChip(onClick = onAskAi)

                // Label notifications info status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (medicine.isReminderEnabled) "Reminders On" else "Silent",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (medicine.isReminderEnabled) Color(0xFF4CAF50) else Color.Gray
                    )
                }
            }
        }
    }
}

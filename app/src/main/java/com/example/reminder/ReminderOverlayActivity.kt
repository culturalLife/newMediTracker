package com.example.reminder

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MedTrackApplication
import com.example.data.model.DoseLog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ReminderOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lock screen & wake up the device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        enableEdgeToEdge()

        val medicineId = intent.getIntExtra("MEDICINE_ID", -1)
        val profileId = intent.getIntExtra("PROFILE_ID", -1)
        val medicineName = intent.getStringExtra("MEDICINE_NAME") ?: "Medicine"
        val dosage = intent.getStringExtra("MEDICINE_DOSAGE") ?: "1 Dose"
        val timeStr = intent.getStringExtra("TIME_STR") ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        setContent {
            MyApplicationTheme {
                Scaffold { innerPadding ->
                    ReminderOverlayScreen(
                        medicineId = medicineId,
                        profileId = profileId,
                        medicineName = medicineName,
                        dosage = dosage,
                        scheduledTime = timeStr,
                        modifier = Modifier.padding(innerPadding),
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderOverlayScreen(
    medicineId: Int,
    profileId: Int,
    medicineName: String,
    dosage: String,
    scheduledTime: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isSuccessAnimating by remember { mutableStateOf(false) }

    // Pulsing circle animation simulating custom Lottie medication pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val opacityPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_opacity"
    )

    // Handle button actions
    val handleTake = {
        isSuccessAnimating = true
        coroutineScope.launch {
            val repository = MedTrackApplication.instance.repository
            val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            
            // Populates logs just in case they don't exist yet, then updates
            repository.populateLogsForDate(profileId, LocalDate.now())
            val existing = repository.getLogsForProfileAndDate(profileId, dateStr)
            
            launch(Dispatchers.IO) {
                // Find matching pending/missed log and mark Taken
                val logs = MedTrackApplication.instance.database.doseLogDao().getLogsForProfileAndDateSync(profileId, dateStr)
                val targetLog = logs.find { it.medicineId == medicineId && it.scheduledTime == scheduledTime }
                if (targetLog != null) {
                    repository.updateLog(
                        targetLog.copy(
                            status = "Taken",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    // Create direct Taken log if none exists
                    repository.insertLog(
                        DoseLog(
                            profileId = profileId,
                            medicineId = medicineId,
                            medicineName = medicineName,
                            dosage = dosage,
                            scheduledTime = scheduledTime,
                            dateStr = dateStr,
                            status = "Taken",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                
                // Cancel notification banner for this medication
                val notificationManager = MedTrackApplication.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(medicineId)
            }

            // Beautiful success transition delay to let user see feedback
            delay(1200)
            onDismiss()
        }
    }

    val handleSnooze = {
        coroutineScope.launch {
            ReminderScheduler.scheduleSnoozeAlarm(
                MedTrackApplication.instance,
                profileId,
                medicineId,
                medicineName,
                dosage,
                10 // snooze for 10 minutes
            )
            // Cancel notification
            val notificationManager = MedTrackApplication.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(medicineId)
            onDismiss()
        }
    }

    val handleSkip = {
        coroutineScope.launch {
            val repository = MedTrackApplication.instance.repository
            val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            
            repository.populateLogsForDate(profileId, LocalDate.now())
            launch(Dispatchers.IO) {
                val logs = MedTrackApplication.instance.database.doseLogDao().getLogsForProfileAndDateSync(profileId, dateStr)
                val targetLog = logs.find { it.medicineId == medicineId && it.scheduledTime == scheduledTime }
                if (targetLog != null) {
                    repository.updateLog(
                        targetLog.copy(
                            status = "Skipped",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    repository.insertLog(
                        DoseLog(
                            profileId = profileId,
                            medicineId = medicineId,
                            medicineName = medicineName,
                            dosage = dosage,
                            scheduledTime = scheduledTime,
                            dateStr = dateStr,
                            status = "Skipped",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                val notificationManager = MedTrackApplication.instance.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(medicineId)
            }
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!isSuccessAnimating) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize()
            ) {
                // Top header details
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Text(
                        text = "MEDICATION REMINDER",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scheduled for $scheduledTime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                // Center visual animation (Pulsating Pill Icon setup simulating the required Lottie animation)
                /* Lottie-equivalent check: In a production app, the raw JSON can be requested from Lottiefiles.com
                   (e.g., Medicine pill bounce animations: https://lottiefiles.com/animations/medicine-pill-J5E0rM2F3g) */
                Box(
                    modifier = Modifier
                        .size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsating ring behind pill
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scalePulse)
                    ) {
                        drawCircle(
                            color = Color(0xFF6200EE).copy(alpha = 0.12f * opacityPulse),
                            radius = size.minDimension / 2f
                        )
                        drawCircle(
                            color = Color(0xFF6200EE).copy(alpha = 0.25f * opacityPulse),
                            radius = size.minDimension / 2.8f,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // Floating Pill visual
                    Card(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(scalePulse),
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "💊",
                                fontSize = 42.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Middle description
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = medicineName,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = dosage,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // Buttons container
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    // "YES, I TOOK IT" button
                    Button(
                        onClick = { handleTake() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "YES, I TOOK IT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "NOT YET" button
                    OutlinedButton(
                        onClick = { handleSnooze() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NOT YET (Snooze 10m)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // "Skip this dose" text link
                    TextButton(
                        onClick = { handleSkip() }
                    ) {
                        Text(
                            text = "Skip this dose",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // Success burst animation
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Card(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "✅",
                            fontSize = 60.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Dose Logged Successfully!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Keep up the excellent work!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

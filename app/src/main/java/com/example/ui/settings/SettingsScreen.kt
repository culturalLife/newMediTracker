package com.example.ui.settings

import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.caregiver.CaregiverNotifier
import com.example.data.model.Profile
import com.example.data.preferences.AppLockPreferences
import com.example.ui.lock.PinSetupDialog

/**
 * Single Settings page that bundles all per-user configuration:
 *   - Privacy Shield (biometric + PIN lock)
 *   - Caregiver contact (name + phone for SMS deep-links)
 *
 * Pure UI — every persistent action is delegated to a callback so the host can
 * fan out to AppLockPreferences and the MainViewModel as appropriate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: Profile?,
    appLockPreferences: AppLockPreferences,
    onProfileUpdated: (Profile) -> Unit,
    onTestCaregiverSms: (Profile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val biometricManager = remember { BiometricManager.from(context) }

    // Lock state — we use plain mutableState that mirrors prefs so toggles feel instant.
    var lockEnabled by remember { mutableStateOf(appLockPreferences.isLockEnabled) }
    var biometricEnabled by remember { mutableStateOf(appLockPreferences.biometricEnabled) }
    var hasPin by remember { mutableStateOf(appLockPreferences.hasPin()) }
    var showPinDialog by remember { mutableStateOf(false) }

    val biometricAvailable = remember {
        biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Caregiver state mirrors profile fields locally so the user can edit + Save.
    var caregiverName by remember(profile?.id) { mutableStateOf(profile?.caregiverName.orEmpty()) }
    var caregiverPhone by remember(profile?.id) { mutableStateOf(profile?.caregiverPhone.orEmpty()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            // ─── Privacy Shield ─────────────────────────────────────────────
            item {
                SectionCard(
                    icon = Icons.Filled.Lock,
                    title = "Privacy Shield",
                    subtitle = "Lock the app behind biometric or PIN before it shows your medication list."
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Enable app lock",
                        subtitle = "Required to use any of the unlock options below",
                        checked = lockEnabled,
                        testTag = "lock_enabled_switch",
                        onCheckedChange = { newValue ->
                            if (newValue && !appLockPreferences.hasPin()) {
                                // Forcing the user to set a PIN before enabling — biometric
                                // alone isn't sufficient because it can become unavailable.
                                showPinDialog = true
                            } else {
                                appLockPreferences.isLockEnabled = newValue
                                lockEnabled = newValue
                                hasPin = appLockPreferences.hasPin()
                            }
                        }
                    )
                    if (lockEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsToggleRow(
                            title = "Biometric unlock",
                            subtitle = if (biometricAvailable)
                                "Use fingerprint or face when available"
                            else
                                "No biometric hardware/enrollment detected — PIN only",
                            checked = biometricEnabled && biometricAvailable,
                            enabled = biometricAvailable,
                            testTag = "biometric_switch",
                            onCheckedChange = { newValue ->
                                appLockPreferences.biometricEnabled = newValue
                                biometricEnabled = newValue
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showPinDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("change_pin_button")
                        ) {
                            Text(if (hasPin) "Change PIN" else "Set PIN")
                        }
                    }
                }
            }

            // ─── Caregiver contact ─────────────────────────────────────────
            item {
                SectionCard(
                    icon = Icons.Filled.Favorite,
                    title = "Caregiver Contact",
                    subtitle = "Stored locally on this device. Used to open your SMS app with a prefilled message — no automatic sending."
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = caregiverName,
                        onValueChange = { caregiverName = it },
                        label = { Text("Caregiver name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("caregiver_name_field")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = caregiverPhone,
                        onValueChange = { caregiverPhone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' } },
                        label = { Text("Phone number") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("caregiver_phone_field")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                profile?.let {
                                    val updated = it.copy(
                                        caregiverName = caregiverName.trim().ifBlank { null },
                                        caregiverPhone = caregiverPhone.trim().ifBlank { null }
                                    )
                                    onProfileUpdated(updated)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_caregiver_button"),
                            enabled = profile != null
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = { profile?.let(onTestCaregiverSms) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_caregiver_sms_button"),
                            enabled = profile != null && caregiverPhone.isNotBlank()
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test SMS")
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onConfirm = { newPin ->
                appLockPreferences.setPin(newPin)
                hasPin = true
                if (!appLockPreferences.isLockEnabled) {
                    appLockPreferences.isLockEnabled = true
                    lockEnabled = true
                }
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false }
        )
    }
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.3f else 0.15f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

package com.example.ui.lock

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.preferences.AppLockPreferences

/**
 * Full-screen lock UI shown before any sensitive content when the Privacy Shield
 * is enabled. Offers biometric unlock first when available + enabled, with a
 * fall-back PIN keypad always present underneath.
 *
 * Behavior:
 *   - On first composition we auto-prompt biometric if available and enabled.
 *   - If biometric isn't an option (no hardware, not enrolled, user disabled it
 *     in Settings), we just sit on the PIN keypad.
 *   - The user can manually retry biometric via the fingerprint icon button.
 *   - PIN entry verifies against [AppLockPreferences]. After 5 failed attempts
 *     the keypad locks for 30 seconds (basic anti-brute-force).
 */
@Composable
fun AppLockScreen(
    preferences: AppLockPreferences,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricManager = remember { BiometricManager.from(context) }

    val canUseBiometric = remember(preferences.biometricEnabled) {
        preferences.biometricEnabled &&
            biometricManager.canAuthenticate(BIOMETRIC_FLAGS) == BiometricManager.BIOMETRIC_SUCCESS &&
            activity != null
    }

    var entered by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockoutEndMs by remember { mutableStateOf(0L) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isShake by remember { mutableStateOf(false) }
    val shakeOffset by animateDpAsState(
        targetValue = if (isShake) 12.dp else 0.dp,
        animationSpec = spring(),
        label = "pin_shake",
        finishedListener = { isShake = false }
    )

    val pinLength = 4 // we accept 4-6 but UI matches the user's stored length on the fly
    val now = System.currentTimeMillis()
    val isLockedOut = lockoutEndMs > now

    // Trigger biometric automatically on first composition.
    LaunchedEffect(Unit) {
        if (canUseBiometric && activity != null) {
            promptBiometric(activity, onSuccess = onUnlocked, onSoftFail = { msg ->
                statusMessage = msg
            })
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "MediTracker is locked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (canUseBiometric) "Use your fingerprint or enter your PIN."
                else "Enter your PIN to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(28.dp))

            // PIN dot indicator
            Row(
                modifier = Modifier.padding(horizontal = shakeOffset),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(pinLength) { idx ->
                    val filled = idx < entered.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            AnimatedVisibility(visible = statusMessage != null) {
                Text(
                    text = statusMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (isLockedOut) {
                val secondsLeft = ((lockoutEndMs - now) / 1000).coerceAtLeast(1)
                Text(
                    text = "Too many attempts — try again in ${secondsLeft}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            PinKeypad(
                enabled = !isLockedOut,
                onDigit = { digit ->
                    statusMessage = null
                    if (entered.length < 6) entered += digit
                    if (entered.length >= pinLength) {
                        val ok = preferences.verifyPin(entered)
                        if (ok) {
                            failedAttempts = 0
                            onUnlocked()
                        } else {
                            failedAttempts++
                            isShake = true
                            entered = ""
                            statusMessage = "Incorrect PIN"
                            if (failedAttempts >= MAX_ATTEMPTS) {
                                lockoutEndMs = System.currentTimeMillis() + LOCKOUT_MS
                                failedAttempts = 0
                            }
                        }
                    }
                },
                onBackspace = {
                    if (entered.isNotEmpty()) entered = entered.dropLast(1)
                    statusMessage = null
                },
                onBiometric = if (canUseBiometric && activity != null) {
                    {
                        statusMessage = null
                        promptBiometric(activity, onSuccess = onUnlocked, onSoftFail = { msg ->
                            statusMessage = msg
                        })
                    }
                } else null
            )
        }
    }
}

@Composable
private fun PinKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("FP", "0", "DEL")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    when (key) {
                        "DEL" -> KeypadAction(
                            enabled = enabled,
                            testTag = "pin_backspace",
                            onClick = onBackspace
                        ) {
                            // material-icons-core doesn't ship Backspace; use Close as a
                            // semantic equivalent ("clear last digit") to avoid pulling
                            // material-icons-extended.
                            Icon(Icons.Filled.Close, contentDescription = "Backspace")
                        }
                        "FP" -> if (onBiometric != null) {
                            KeypadAction(
                                enabled = enabled,
                                testTag = "pin_biometric",
                                onClick = onBiometric
                            ) {
                                // Fingerprint glyph rendered as a Text emoji — keeps us
                                // off the extended icons library.
                                Text(
                                    text = "\uD83D\uDC46", // 👆
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            // Empty slot to keep grid alignment when biometric isn't available.
                            Box(modifier = Modifier.size(72.dp))
                        }
                        else -> KeypadDigit(
                            digit = key.first(),
                            enabled = enabled,
                            onClick = onDigit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadDigit(digit: Char, enabled: Boolean, onClick: (Char) -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.6f else 0.3f))
            .clickable(enabled = enabled) { onClick(digit) }
            .testTag("pin_key_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun KeypadAction(
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.6f else 0.3f))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Triggers the AndroidX BiometricPrompt. Soft failures (user cancel, lockout)
 * are reported via [onSoftFail] so the screen can show a hint without crashing.
 * Hard errors (no hardware) are silently ignored — the PIN keypad remains.
 */
private fun promptBiometric(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onSoftFail: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // Suppress the noisy "user canceled" error — that's not a failure to surface.
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                errorCode != BiometricPrompt.ERROR_CANCELED
            ) {
                onSoftFail(errString.toString())
            }
        }
        override fun onAuthenticationFailed() {
            onSoftFail("Biometric not recognized — try PIN")
        }
    }
    val prompt = BiometricPrompt(activity, executor, callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock MediTracker")
        .setSubtitle("Use your fingerprint or face to continue")
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BIOMETRIC_FLAGS)
        .build()
    prompt.authenticate(info)
}

private const val MAX_ATTEMPTS = 5
private const val LOCKOUT_MS = 30_000L
private const val BIOMETRIC_FLAGS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

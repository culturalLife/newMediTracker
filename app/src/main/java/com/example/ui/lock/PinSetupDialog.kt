package com.example.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Two-step PIN setup dialog. The user enters a PIN, then confirms it. Only when both
 * match exactly do we call [onConfirm]. Display formatting is masked using the
 * standard password visual transformation so shoulder-surfing is mitigated.
 */
@Composable
fun PinSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val pinValid = pin.length in 4..6 && pin.all { it.isDigit() }
    val match = pin == confirm
    val canSubmit = pinValid && match

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a PIN", fontWeight = FontWeight.Black) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Choose a 4–6 digit PIN. You'll use this when biometric isn't available.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { newValue ->
                        if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                            pin = newValue
                            showError = false
                        }
                    },
                    label = { Text("Enter PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_setup_field")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { newValue ->
                        if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                            confirm = newValue
                            showError = false
                        }
                    },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_confirm_field")
                )
                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (!pinValid) "PIN must be 4–6 digits."
                        else "PINs don't match.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = pin.isNotEmpty() && confirm.isNotEmpty(),
                onClick = {
                    if (canSubmit) {
                        onConfirm(pin)
                    } else {
                        showError = true
                    }
                }
            ) {
                Text("Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

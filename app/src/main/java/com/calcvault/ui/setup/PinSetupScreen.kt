package com.calcvault.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

/** What a PIN is being chosen for, which only changes the wording. */
enum class PinPurpose(val title: String, val explanation: String) {
    CREATE(
        "Choose your unlock code",
        "Type this code into the calculator and press = to open the vault. " +
            "There is no recovery: if you forget it, the files are gone for good.",
    ),
    CHANGE(
        "Change your unlock code",
        "Your hidden files stay exactly where they are; only the code changes.",
    ),
    DECOY(
        "Set a decoy code",
        "A second code that opens a separate, empty vault. Useful if you are ever " +
            "made to unlock the app in front of someone.",
    ),
}

/**
 * Collects a PIN twice.
 *
 * The minimum length matters: the calculator only treats an entry as a possible
 * PIN when it is at least four digits, so anything shorter would never unlock.
 */
@Composable
fun PinSetupScreen(
    purpose: PinPurpose,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    /** Returns an error message when the value is unacceptable, or null. */
    validate: (String) -> String? = { null },
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(purpose.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = purpose.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )

            OutlinedTextField(
                value = pin,
                onValueChange = { input -> pin = input.filter(Char::isDigit).take(MAX_PIN) },
                label = { Text("Code") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirmation,
                onValueChange = { input -> confirmation = input.filter(Char::isDigit).take(MAX_PIN) },
                label = { Text("Repeat code") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    error = when {
                        pin.length < MIN_PIN -> "Use at least $MIN_PIN digits"
                        pin != confirmation -> "The two codes do not match"
                        else -> validate(pin)
                    }
                    if (error == null) onConfirm(pin)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

private const val MIN_PIN = 4
private const val MAX_PIN = 12

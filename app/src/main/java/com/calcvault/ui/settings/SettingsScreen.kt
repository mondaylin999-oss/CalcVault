package com.calcvault.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.calcvault.AppContainer
import com.calcvault.security.AppSettings
import java.text.DateFormat
import java.util.Date

/**
 * Everything about how the vault protects itself, plus the two destructive
 * actions, which are deliberately at the bottom behind confirmations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onSetDecoyPin: () -> Unit,
    onVaultDestroyed: () -> Unit,
) {
    val settings = container.settings
    var autoLock by remember { mutableStateOf(settings.autoLockSeconds) }
    var intruder by remember { mutableStateOf(settings.intruderCaptureEnabled) }
    var blockScreenshots by remember { mutableStateOf(settings.blockScreenshots) }
    var showIntruders by remember { mutableStateOf(false) }
    var confirmDestroy by remember { mutableStateOf(false) }
    var confirmRemoveDecoy by remember { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        intruder = granted
        settings.intruderCaptureEnabled = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Access")
            SettingRow(
                title = "Change unlock code",
                subtitle = "Your hidden files stay readable",
                onClick = onChangePin,
            )
            SettingRow(
                title = if (container.pinStore.hasDecoy()) "Change decoy code" else "Set a decoy code",
                subtitle = "A second code that opens a separate, empty vault",
                onClick = onSetDecoyPin,
            )
            if (container.pinStore.hasDecoy()) {
                SettingRow(
                    title = "Remove decoy code",
                    subtitle = "The decoy vault and its contents are erased",
                    onClick = { confirmRemoveDecoy = true },
                )
            }

            Divider()
            SectionHeader("Locking")
            SettingRow(
                title = "Auto-lock",
                subtitle = AppSettings.describeAutoLock(autoLock),
                onClick = {
                    val choices = AppSettings.AUTO_LOCK_CHOICES
                    val next = choices[(choices.indexOf(autoLock) + 1) % choices.size]
                    autoLock = next
                    settings.autoLockSeconds = next
                },
            )
            ToggleRow(
                title = "Block screenshots",
                subtitle = "Also hides the vault from the recent-apps preview",
                checked = blockScreenshots,
                onChange = {
                    blockScreenshots = it
                    settings.blockScreenshots = it
                },
            )

            Divider()
            SectionHeader("Intruders")
            ToggleRow(
                title = "Photograph failed attempts",
                subtitle = "Silently uses the front camera after " +
                    "${settings.intruderThreshold} wrong codes",
                checked = intruder,
                onChange = { wanted ->
                    if (wanted && !container.intruderLog.hasCameraPermission()) {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    } else {
                        intruder = wanted
                        settings.intruderCaptureEnabled = wanted
                    }
                },
            )
            SettingRow(
                title = "Failed attempts",
                subtitle = "${container.intruderLog.list().size} recorded",
                onClick = { showIntruders = true },
            )

            Divider()
            SectionHeader("Danger zone")
            SettingRow(
                title = "Erase this vault",
                subtitle = "Deletes every hidden file and forgets the code",
                onClick = { confirmDestroy = true },
                destructive = true,
            )

            Text(
                text = "CalcVault keeps everything on this device. The app has no " +
                    "internet permission, so nothing you hide can be uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    if (showIntruders) {
        IntruderDialog(container, onDismiss = { showIntruders = false })
    }

    if (confirmRemoveDecoy) {
        ConfirmDialog(
            title = "Remove the decoy?",
            body = "The decoy vault and anything inside it will be erased.",
            confirmLabel = "Remove",
            onConfirm = {
                container.pinStore.forget(com.calcvault.crypto.VaultId.DECOY)
                confirmRemoveDecoy = false
            },
            onDismiss = { confirmRemoveDecoy = false },
        )
    }

    if (confirmDestroy) {
        ConfirmDialog(
            title = "Erase this vault?",
            body = "Every hidden file is deleted and the unlock code is forgotten. " +
                "This cannot be undone.",
            confirmLabel = "Erase",
            onConfirm = {
                val open = container.session.current.value
                open?.repository?.destroy()
                open?.vaultId?.let(container.pinStore::forget)
                container.session.lock()
                confirmDestroy = false
                onVaultDestroyed()
            },
            onDismiss = { confirmDestroy = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IntruderDialog(container: AppContainer, onDismiss: () -> Unit) {
    val attempts = remember { container.intruderLog.list() }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Failed attempts") },
        text = {
            if (attempts.isEmpty()) {
                Text("No failed unlock attempts have been recorded.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attempts) { attempt ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val photo = remember(attempt.file.path) {
                                container.intruderLog.readPhoto(attempt)?.let { bytes ->
                                    android.graphics.BitmapFactory
                                        .decodeByteArray(bytes, 0, bytes.size)
                                        ?.asImageBitmap()
                                }
                            }
                            if (photo != null) {
                                Image(
                                    bitmap = photo,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                )
                            }
                            Text(
                                text = "  " + format.format(Date(attempt.timestampMillis)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    container.intruderLog.clear()
                    onDismiss()
                },
            ) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

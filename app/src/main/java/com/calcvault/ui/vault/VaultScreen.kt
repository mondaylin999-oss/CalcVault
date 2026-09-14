package com.calcvault.ui.vault

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcvault.vault.VaultCategory
import com.calcvault.vault.VaultEntry

/**
 * The vault: what the calculator hides.
 *
 * Reached only by typing the PIN into the calculator, and left by the lock
 * button, the back gesture, or the app going to the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onLock: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var viewing by remember { mutableStateOf<VaultEntry?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importAll(uris) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isDecoy) "Vault (decoy)" else "Vault")
                },
                actions = {
                    if (state.selection.isNotEmpty()) {
                        IconButton(onClick = viewModel::deleteSelected) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onLock) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock vault")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { picker.launch(mimeFilter(state.category)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Hide files")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = VaultCategory.entries.indexOf(state.category)) {
                VaultCategory.entries.forEach { category ->
                    Tab(
                        selected = category == state.category,
                        onClick = { viewModel.selectCategory(category) },
                        text = { Text(category.label) },
                    )
                }
            }

            val entries = state.entries.filter { it.category == state.category }
            if (state.busyMessage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "  ${state.busyMessage}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (entries.isEmpty() && state.busyMessage == null) {
                EmptyState(state.category)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        EntryCell(
                            entry = entry,
                            selected = entry.id in state.selection,
                            selectionActive = state.selection.isNotEmpty(),
                            viewModel = viewModel,
                            onOpen = { viewing = entry },
                            onToggleSelect = { viewModel.toggleSelection(entry) },
                        )
                    }
                }
            }
        }
    }

    viewing?.let { entry ->
        EntryViewer(
            entry = entry,
            viewModel = viewModel,
            onClose = { viewing = null },
        )
    }
}

private fun mimeFilter(category: VaultCategory): Array<String> = when (category) {
    VaultCategory.PHOTO -> arrayOf("image/*")
    VaultCategory.VIDEO -> arrayOf("video/*")
    VaultCategory.FILE -> arrayOf("*/*")
}

@Composable
private fun EmptyState(category: VaultCategory) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No ${category.label.lowercase()} hidden yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Everything you add is encrypted on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCell(
    entry: VaultEntry,
    selected: Boolean,
    selectionActive: Boolean,
    viewModel: VaultViewModel,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, entry.id) {
        value = viewModel.thumbnail(entry)
    }

    Column(
        modifier = Modifier.combinedClickable(
            onClick = { if (selectionActive) onToggleSelect() else onOpen() },
            onLongClick = onToggleSelect,
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val image = thumbnail
            when {
                image != null -> Image(
                    bitmap = image,
                    contentDescription = entry.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                entry.category == VaultCategory.VIDEO -> Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                else -> Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = Formatter.formatShortFileSize(LocalContext.current, entry.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

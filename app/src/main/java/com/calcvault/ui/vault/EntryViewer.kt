package com.calcvault.ui.vault

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calcvault.vault.VaultCategory
import com.calcvault.vault.VaultEntry
import java.io.File

/**
 * Full-screen viewer for one hidden item.
 *
 * Photos are decrypted straight into a bitmap. Video is the exception: a player
 * needs a real file, so the clip is decrypted into the private cache and wiped
 * again as soon as the viewer closes.
 */
@Composable
fun EntryViewer(
    entry: VaultEntry,
    viewModel: VaultViewModel,
    onClose: () -> Unit,
) {
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(entry.mimeType),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportEntry(entry, uri)
            onClose()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when (entry.category) {
                VaultCategory.PHOTO -> PhotoContent(entry, viewModel)
                VaultCategory.VIDEO -> VideoContent(entry, viewModel)
                VaultCategory.FILE -> FileContent(entry)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = entry.displayName,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { exporter.launch(entry.displayName) }) {
                    Icon(
                        Icons.Filled.Unarchive,
                        contentDescription = "Unhide and save out",
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.toggleSelection(entry)
                        viewModel.deleteSelected()
                        onClose()
                    },
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PhotoContent(entry: VaultEntry, viewModel: VaultViewModel) {
    val image by produceState<ImageBitmap?>(null, entry.id) { value = viewModel.fullImage(entry) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = entry.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VideoContent(entry: VaultEntry, viewModel: VaultViewModel) {
    val file by produceState<File?>(null, entry.id) { value = viewModel.playableFile(entry) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val playable = file
        if (playable == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    VideoView(context).apply {
                        setMediaController(MediaController(context).also { it.setAnchorView(this) })
                        setVideoPath(playable.absolutePath)
                        setOnPreparedListener { start() }
                    }
                },
            )
            // The decrypted copy exists only while the viewer is on screen.
            DisposableEffect(playable) {
                onDispose { playable.delete() }
            }
        }
    }
}

@Composable
private fun FileContent(entry: VaultEntry) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = entry.displayName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = entry.mimeType,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "Use the unhide button to save this file back out.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp, start = 32.dp, end = 32.dp),
            )
        }
    }
}

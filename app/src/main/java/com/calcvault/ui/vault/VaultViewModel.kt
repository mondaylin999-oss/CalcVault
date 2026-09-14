package com.calcvault.ui.vault

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcvault.AppContainer
import com.calcvault.vault.VaultCategory
import com.calcvault.vault.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VaultUiState(
    val category: VaultCategory = VaultCategory.PHOTO,
    val entries: List<VaultEntry> = emptyList(),
    val busyMessage: String? = null,
    val message: String? = null,
    val selection: Set<String> = emptySet(),
    val isDecoy: Boolean = false,
)

class VaultViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    /** Thumbnails are decrypted on demand and cached only for this session. */
    private val thumbnails = mutableMapOf<String, ImageBitmap?>()

    init {
        refresh()
    }

    private val repository get() = container.session.current.value?.repository

    fun refresh() {
        val repo = repository ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                entries = repo.listEntries(),
                isDecoy = container.session.current.value?.vaultId?.name == "DECOY",
            )
        }
    }

    fun selectCategory(category: VaultCategory) {
        _state.value = _state.value.copy(category = category, selection = emptySet())
    }

    fun toggleSelection(entry: VaultEntry) {
        val selection = _state.value.selection
        _state.value = _state.value.copy(
            selection = if (entry.id in selection) selection - entry.id else selection + entry.id,
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selection = emptySet())
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun visibleEntries(): List<VaultEntry> =
        _state.value.entries.filter { it.category == _state.value.category }

    // ---- Actions ---------------------------------------------------------------

    fun importAll(uris: List<Uri>) {
        val repo = repository ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var imported = 0
            var failed = 0
            uris.forEachIndexed { index, uri ->
                _state.value = _state.value.copy(
                    busyMessage = "Encrypting ${index + 1} of ${uris.size}…",
                )
                runCatching { repo.importFile(uri) }
                    .onSuccess { imported++ }
                    .onFailure { failed++ }
            }
            _state.value = _state.value.copy(
                busyMessage = null,
                message = buildString {
                    append("Hid $imported item${if (imported == 1) "" else "s"}")
                    if (failed > 0) append(", $failed could not be read")
                },
            )
            refresh()
        }
    }

    fun deleteSelected() {
        val repo = repository ?: return
        val ids = _state.value.selection
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busyMessage = "Deleting…")
            _state.value.entries.filter { it.id in ids }.forEach { repo.delete(it) }
            _state.value = _state.value.copy(
                busyMessage = null,
                selection = emptySet(),
                message = "Deleted ${ids.size} item${if (ids.size == 1) "" else "s"}",
            )
            refresh()
        }
    }

    /** Writes one item back out to a location the user picked, then unhides it. */
    fun exportEntry(entry: VaultEntry, destination: Uri) {
        val repo = repository ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busyMessage = "Restoring…")
            val result = runCatching { repo.exportAndRemove(entry, destination) }
            _state.value = _state.value.copy(
                busyMessage = null,
                message = if (result.isSuccess) {
                    "Restored ${entry.displayName}"
                } else {
                    "Could not restore ${entry.displayName}"
                },
            )
            refresh()
        }
    }

    // ---- Media -----------------------------------------------------------------

    suspend fun thumbnail(entry: VaultEntry): ImageBitmap? {
        if (entry.category != VaultCategory.PHOTO) return null
        thumbnails[entry.id]?.let { return it }
        if (thumbnails.containsKey(entry.id)) return null
        val repo = repository ?: return null
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = repo.readBytes(entry)
                // Decode downscaled: a grid cell never needs the full image.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, THUMBNAIL_PIXELS)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }.getOrNull()
        }
        thumbnails[entry.id] = bitmap
        return bitmap
    }

    suspend fun fullImage(entry: VaultEntry): ImageBitmap? {
        val repo = repository ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = repo.readBytes(entry)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    /** Decrypts a video to private cache so a player can open it. */
    suspend fun playableFile(entry: VaultEntry): File? {
        val repo = repository ?: return null
        return runCatching { repo.materialize(entry) }.getOrNull()
    }

    private fun sampleSizeFor(width: Int, target: Int): Int {
        var sample = 1
        while (width / sample > target * 2) sample *= 2
        return sample
    }

    fun lock() {
        thumbnails.clear()
        container.session.lock()
    }

    private companion object {
        const val THUMBNAIL_PIXELS = 300
    }
}

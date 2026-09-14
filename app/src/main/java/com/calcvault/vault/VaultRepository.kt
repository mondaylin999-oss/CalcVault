package com.calcvault.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.calcvault.crypto.CryptoManager
import com.calcvault.crypto.VaultId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Reads and writes the encrypted contents of one vault.
 *
 * Every instance is bound to the vault key held by the current session, so it
 * becomes useless as soon as the vault locks and the key is dropped.
 */
class VaultRepository(
    private val context: Context,
    private val vaultId: VaultId,
    private val key: SecretKey,
) {
    /** Exposed so a PIN change can re-wrap the same key without reading any file. */
    val sessionKey: SecretKey get() = key

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val root: File
        get() = File(context.filesDir, "vault/${vaultId.directory}").apply { mkdirs() }

    private val indexFile: File
        get() = File(root, "index.bin")

    private val blobDir: File
        get() = File(root, "blobs").apply { mkdirs() }

    /** Scratch space for media that a player can only open as a real file. */
    private val scratchDir: File
        get() = File(context.cacheDir, "vault-scratch").apply { mkdirs() }

    // ---- Index -----------------------------------------------------------------

    suspend fun listEntries(): List<VaultEntry> = withContext(Dispatchers.IO) {
        readIndex().entries.sortedByDescending { it.addedAtMillis }
    }

    private fun readIndex(): VaultIndex {
        if (!indexFile.exists()) return VaultIndex()
        return runCatching {
            json.decodeFromString<VaultIndex>(
                String(CryptoManager.decrypt(key, indexFile.readBytes()))
            )
        }.getOrElse { VaultIndex() }
    }

    private fun writeIndex(index: VaultIndex) {
        val encoded = json.encodeToString(VaultIndex.serializer(), index)
        val payload = CryptoManager.encrypt(key, encoded.toByteArray())
        // Write through a temp file so a crash mid-write cannot lose the index.
        val temp = File(root, "index.tmp")
        temp.writeBytes(payload)
        if (!temp.renameTo(indexFile)) {
            indexFile.writeBytes(payload)
            temp.delete()
        }
    }

    // ---- Importing -------------------------------------------------------------

    /**
     * Copies the content behind [uri] into the vault, encrypting it on the way
     * in, and returns the new entry.
     *
     * The source is never deleted here; the caller decides whether to ask the
     * system to remove the original after a successful import.
     */
    suspend fun importFile(uri: Uri): VaultEntry = withContext(Dispatchers.IO) {
        val (name, size) = queryMetadata(uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val id = UUID.randomUUID().toString()
        val target = File(blobDir, id)

        var written = 0L
        context.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "Cannot read $name" }
            target.outputStream().use { rawOut ->
                CryptoManager.encryptingStream(key, rawOut).use { encrypted ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        encrypted.write(buffer, 0, read)
                        written += read
                    }
                }
            }
        }

        val entry = VaultEntry(
            id = id,
            displayName = name,
            mimeType = mimeType,
            sizeBytes = if (size > 0) size else written,
            addedAtMillis = System.currentTimeMillis(),
            category = VaultCategory.fromMimeType(mimeType),
            originalLocation = uri.toString(),
        )
        val index = readIndex()
        writeIndex(index.copy(entries = index.entries + entry))
        entry
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long> {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameColumn >= 0) cursor.getString(nameColumn) else null
                val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    cursor.getLong(sizeColumn)
                } else {
                    0L
                }
                if (!name.isNullOrBlank()) return name to size
            }
        }
        return (uri.lastPathSegment ?: "item") to 0L
    }

    // ---- Reading ---------------------------------------------------------------

    /**
     * Decrypts a whole entry into memory. Only safe for images and small files;
     * video goes through [materialize] instead.
     */
    suspend fun readBytes(entry: VaultEntry): ByteArray = withContext(Dispatchers.IO) {
        File(blobDir, entry.id).inputStream().use { raw ->
            CryptoManager.decryptingStream(key, raw).use { it.readBytes() }
        }
    }

    /**
     * Decrypts an entry to a temporary file so a media player can open it.
     *
     * This is the one moment plaintext touches the disk. The file lives in the
     * app's private cache and is deleted by [clearScratch], which runs whenever
     * the vault locks.
     */
    suspend fun materialize(entry: VaultEntry): File = withContext(Dispatchers.IO) {
        val target = File(scratchDir, entry.id + extensionOf(entry))
        if (!target.exists() || target.length() == 0L) {
            File(blobDir, entry.id).inputStream().use { raw ->
                CryptoManager.decryptingStream(key, raw).use { plain ->
                    target.outputStream().use { plain.copyTo(it) }
                }
            }
        }
        target
    }

    private fun extensionOf(entry: VaultEntry): String {
        val suffix = entry.displayName.substringAfterLast('.', "")
        return if (suffix.isEmpty()) "" else ".$suffix"
    }

    /** Wipes every decrypted temporary file. Called on lock and on app stop. */
    fun clearScratch() {
        scratchDir.listFiles()?.forEach { it.delete() }
    }

    // ---- Removing --------------------------------------------------------------

    /** Writes the plaintext back out to [destination], then removes it from the vault. */
    suspend fun exportAndRemove(entry: VaultEntry, destination: Uri) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(destination).use { output ->
                checkNotNull(output) { "Cannot write to the chosen location" }
                File(blobDir, entry.id).inputStream().use { raw ->
                    CryptoManager.decryptingStream(key, raw).use { it.copyTo(output) }
                }
            }
        }
        delete(entry)
    }

    suspend fun delete(entry: VaultEntry) {
        withContext(Dispatchers.IO) {
            shred(File(blobDir, entry.id))
            File(scratchDir, entry.id + extensionOf(entry)).delete()
            val index = readIndex()
            writeIndex(index.copy(entries = index.entries.filterNot { it.id == entry.id }))
        }
    }

    /**
     * Overwrites a blob before unlinking it.
     *
     * On a flash-backed filesystem this is not a guarantee, but the payload is
     * encrypted anyway; this just removes the obvious copy.
     */
    private fun shred(file: File) {
        if (!file.exists()) return
        runCatching {
            val length = file.length()
            file.outputStream().use { out ->
                val chunk = DEFAULT_BUFFER_SIZE
                var remaining = length
                while (remaining > 0) {
                    val n = minOf(remaining, chunk.toLong()).toInt()
                    out.write(CryptoManager.randomBytes(n), 0, n)
                    remaining -= n
                }
            }
        }
        file.delete()
    }

    /** Destroys the entire vault directory, index included. */
    fun destroy() {
        clearScratch()
        blobDir.listFiles()?.forEach { shred(it) }
        root.deleteRecursively()
    }
}

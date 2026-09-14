package com.calcvault.vault

import kotlinx.serialization.Serializable

/** The tabs the vault is organised into. */
enum class VaultCategory(val label: String) {
    PHOTO("Photos"),
    VIDEO("Videos"),
    FILE("Files");

    companion object {
        /** Picks a category from a MIME type, defaulting to the generic file tab. */
        fun fromMimeType(mimeType: String?): VaultCategory = when {
            mimeType == null -> FILE
            mimeType.startsWith("image/") -> PHOTO
            mimeType.startsWith("video/") -> VIDEO
            else -> FILE
        }
    }
}

/**
 * One hidden item.
 *
 * This record lives only inside the encrypted index, so even the file names and
 * sizes of hidden items are unreadable without the PIN. On disk the payload is
 * named after [id] alone and carries no extension.
 */
@Serializable
data class VaultEntry(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedAtMillis: Long,
    val category: VaultCategory,
    /** Where the file came from, so "unhide" can offer to put it back. */
    val originalLocation: String? = null,
)

@Serializable
internal data class VaultIndex(
    val version: Int = 1,
    val entries: List<VaultEntry> = emptyList(),
)

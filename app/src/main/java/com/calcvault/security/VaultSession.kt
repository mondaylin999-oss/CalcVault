package com.calcvault.security

import android.content.Context
import com.calcvault.crypto.VaultId
import com.calcvault.vault.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey

/**
 * Holds the unlocked vault key for exactly as long as the vault is open.
 *
 * The key lives only here, in memory. Locking drops it and wipes any decrypted
 * scratch files, so a later screen cannot read the vault without a fresh PIN.
 */
class VaultSession(private val context: Context) {

    data class Open(val vaultId: VaultId, val repository: VaultRepository)

    private val state = MutableStateFlow<Open?>(null)
    val current: StateFlow<Open?> = state.asStateFlow()

    val isUnlocked: Boolean get() = state.value != null

    /** Timestamp of the moment the app went to the background, for auto-lock. */
    @Volatile
    var backgroundedAtMillis: Long = 0L

    fun unlock(vaultId: VaultId, key: SecretKey) {
        state.value = Open(vaultId, VaultRepository(context, vaultId, key))
    }

    fun lock() {
        state.value?.repository?.clearScratch()
        state.value = null
        backgroundedAtMillis = 0L
    }

    /**
     * Locks if the app has been away longer than the configured grace period.
     * Returns true when a lock actually happened.
     */
    fun lockIfExpired(autoLockSeconds: Int): Boolean {
        if (!isUnlocked) return false
        val since = backgroundedAtMillis
        if (since == 0L) return false
        val elapsed = System.currentTimeMillis() - since
        if (elapsed >= autoLockSeconds * 1000L) {
            lock()
            return true
        }
        return false
    }
}

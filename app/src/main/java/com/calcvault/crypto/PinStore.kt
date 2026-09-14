package com.calcvault.crypto

import android.content.Context
import android.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Which of the two vaults a PIN opened. */
enum class VaultId(val directory: String) {
    MAIN("main"),
    DECOY("decoy"),
}

/** Result of checking a PIN typed into the calculator. */
sealed interface UnlockResult {
    data class Success(val vaultId: VaultId, val key: SecretKey) : UnlockResult
    data object WrongPin : UnlockResult
}

/**
 * Persists the wrapped vault keys.
 *
 * Nothing here is secret on its own: a salt and two layers of ciphertext. The
 * PIN is never stored, and neither is any hash that could be attacked offline
 * without the device's Keystore.
 */
class PinStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("calcvault.keys", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = prefs.contains(saltKey(VaultId.MAIN))

    fun hasDecoy(): Boolean = prefs.contains(saltKey(VaultId.DECOY))

    /**
     * Creates (or replaces) a vault for [vaultId] locked by [pin].
     *
     * Replacing a vault's PIN is done by [changePin], not here: this generates a
     * brand-new vault key and so orphans any existing files.
     */
    fun createVault(vaultId: VaultId, pin: CharArray): SecretKey {
        val vaultKey = CryptoManager.newVaultKey()
        persist(vaultId, pin, vaultKey)
        return vaultKey
    }

    /** Re-wraps the existing vault key under a new PIN, leaving files readable. */
    fun changePin(vaultId: VaultId, currentKey: SecretKey, newPin: CharArray) {
        persist(vaultId, newPin, currentKey)
    }

    private fun persist(vaultId: VaultId, pin: CharArray, vaultKey: SecretKey) {
        val salt = CryptoManager.randomBytes(CryptoManager.SALT_BYTES)
        val pinKey = CryptoManager.deriveKeyFromPin(pin, salt)
        val wrapped = CryptoManager.encrypt(pinKey, vaultKey.encoded)
        val sealed = CryptoManager.deviceEncrypt(wrapped)
        prefs.edit()
            .putString(saltKey(vaultId), encode(salt))
            .putString(keyKey(vaultId), encode(sealed))
            .apply()
    }

    /**
     * Tries [pin] against every configured vault. The decoy is checked too, so a
     * second PIN quietly opens a different, harmless-looking vault.
     */
    fun unlock(pin: CharArray): UnlockResult {
        for (vaultId in VaultId.entries) {
            val salt = prefs.getString(saltKey(vaultId), null)?.let(::decode) ?: continue
            val sealed = prefs.getString(keyKey(vaultId), null)?.let(::decode) ?: continue
            val pinKey = CryptoManager.deriveKeyFromPin(pin, salt)
            val vaultKey = runCatching {
                SecretKeySpec(CryptoManager.decrypt(pinKey, CryptoManager.deviceDecrypt(sealed)), "AES")
            }.getOrNull()
            if (vaultKey != null) return UnlockResult.Success(vaultId, vaultKey)
        }
        return UnlockResult.WrongPin
    }

    /** Forgets a vault's key. The files stay on disk but become unreadable. */
    fun forget(vaultId: VaultId) {
        prefs.edit().remove(saltKey(vaultId)).remove(keyKey(vaultId)).apply()
    }

    fun forgetEverything() {
        prefs.edit().clear().apply()
        CryptoManager.destroyDeviceKey()
    }

    private fun saltKey(vaultId: VaultId) = "${vaultId.directory}.salt"
    private fun keyKey(vaultId: VaultId) = "${vaultId.directory}.key"

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
}

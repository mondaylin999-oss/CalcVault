package com.calcvault.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * All cryptography for the vault.
 *
 * Layout of trust:
 *  - A random 256-bit **vault key** encrypts every file and the index. It never
 *    leaves memory in plaintext and is discarded the moment the vault locks.
 *  - The vault key is wrapped by a key derived from the PIN with PBKDF2, so the
 *    PIN itself is never stored in any form.
 *  - That wrapped blob is then encrypted again with a hardware-backed Android
 *    Keystore key, which cannot be exported. Copying the app's data directory to
 *    another device therefore yields nothing, even if the PIN is known.
 */
object CryptoManager {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val WRAP_KEY_ALIAS = "calcvault.wrap.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 210_000

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    /** Generates a fresh vault key. */
    fun newVaultKey(): SecretKey = SecretKeySpec(randomBytes(KEY_BITS / 8), "AES")

    /** Stretches a PIN into an AES key using the stored per-vault salt. */
    fun deriveKeyFromPin(pin: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin, salt, PBKDF2_ITERATIONS, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    // ---- Byte-array encryption -------------------------------------------------

    /** Encrypts to `iv || ciphertext || tag`. */
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * Reverses [encrypt]. Throws when the key is wrong or the bytes were
     * tampered with, which is exactly how a wrong PIN is detected.
     */
    fun decrypt(key: SecretKey, payload: ByteArray): ByteArray {
        require(payload.size > IV_BYTES) { "Ciphertext is truncated" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, payload, 0, IV_BYTES),
        )
        return cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES)
    }

    // ---- Streaming, for media files that must not be held in memory ------------

    /** Wraps [sink] so everything written to it is encrypted; writes the IV first. */
    fun encryptingStream(key: SecretKey, sink: OutputStream): OutputStream {
        val iv = randomBytes(IV_BYTES)
        sink.write(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return CipherOutputStream(sink, cipher)
    }

    /** Wraps [source] so reads come back as plaintext; consumes the leading IV. */
    fun decryptingStream(key: SecretKey, source: InputStream): InputStream {
        val iv = ByteArray(IV_BYTES)
        var read = 0
        while (read < IV_BYTES) {
            val n = source.read(iv, read, IV_BYTES - read)
            if (n < 0) throw IllegalStateException("File is truncated")
            read += n
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return CipherInputStream(source, cipher)
    }

    // ---- Hardware-backed outer layer -------------------------------------------

    /** Encrypts with the non-exportable Keystore key, binding data to this device. */
    fun deviceEncrypt(plaintext: ByteArray): ByteArray = encrypt(deviceKey(), plaintext)

    fun deviceDecrypt(payload: ByteArray): ByteArray = decrypt(deviceKey(), payload)

    private fun deviceKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(WRAP_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        )
        return generator.generateKey()
    }

    /** Removes the device key, which permanently destroys every wrapped vault key. */
    fun destroyDeviceKey() {
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(WRAP_KEY_ALIAS)
        }
    }
}

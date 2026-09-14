package com.calcvault.security

import android.content.Context
import com.calcvault.calc.AngleUnit

/**
 * Non-secret preferences.
 *
 * Deliberately separate from the key material: nothing here reveals whether a
 * vault exists or what is in it, so it is safe to read before unlocking.
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("calcvault.settings", Context.MODE_PRIVATE)

    var angleUnit: AngleUnit
        get() = runCatching { AngleUnit.valueOf(prefs.getString(KEY_ANGLE, null) ?: "DEG") }
            .getOrDefault(AngleUnit.DEG)
        set(value) = prefs.edit().putString(KEY_ANGLE, value.name).apply()

    /** How long the app may sit in the background before the vault re-locks. */
    var autoLockSeconds: Int
        get() = prefs.getInt(KEY_AUTO_LOCK, 30)
        set(value) = prefs.edit().putInt(KEY_AUTO_LOCK, value).apply()

    /** Take a front-camera photo after [intruderThreshold] wrong PINs. */
    var intruderCaptureEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTRUDER, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRUDER, value).apply()

    var intruderThreshold: Int
        get() = prefs.getInt(KEY_INTRUDER_THRESHOLD, 3)
        set(value) = prefs.edit().putInt(KEY_INTRUDER_THRESHOLD, value).apply()

    /** Keeps the vault out of the recent-apps screenshot and blocks screenshots. */
    var blockScreenshots: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_SCREENSHOTS, value).apply()

    /** Running count of consecutive wrong PINs; reset on any successful unlock. */
    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED, value).apply()

    companion object {
        private const val KEY_ANGLE = "angle_unit"
        private const val KEY_AUTO_LOCK = "auto_lock_seconds"
        private const val KEY_INTRUDER = "intruder_capture"
        private const val KEY_INTRUDER_THRESHOLD = "intruder_threshold"
        private const val KEY_BLOCK_SCREENSHOTS = "block_screenshots"
        private const val KEY_FAILED = "failed_attempts"

        val AUTO_LOCK_CHOICES = listOf(0, 15, 30, 60, 300)

        fun describeAutoLock(seconds: Int): String = when (seconds) {
            0 -> "Immediately"
            60 -> "After 1 minute"
            300 -> "After 5 minutes"
            else -> "After $seconds seconds"
        }
    }
}

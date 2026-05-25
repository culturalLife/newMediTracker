package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Persistent settings for the Privacy Shield lock.
 *
 * Backed by a private SharedPreferences file. PINs are stored as salted SHA-256
 * hashes — never as plaintext — so a leak of the prefs file alone cannot reveal
 * the PIN. Biometric availability is determined at runtime by the lock screen.
 *
 * Design tradeoffs:
 *   - We don't use security-crypto / EncryptedSharedPreferences because the
 *     value being stored (a hash) is already non-reversible, and the extra
 *     dependency would balloon the APK for no security gain.
 *   - The salt is 16 random bytes generated once on first PIN setup.
 */
class AppLockPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True when the user has enabled the lock at all. */
    var isLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (!value) clearPin() // disabling the lock should also wipe credentials
        }

    /** True when biometric (fingerprint/face) unlock should be offered. */
    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    /** True once a PIN has been registered. PIN is required as the biometric fallback. */
    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_SALT)

    /**
     * Persists a new 4–6 digit PIN. The plaintext digits are immediately discarded;
     * only the salt + hash live in storage.
     */
    fun setPin(plainPin: String) {
        require(plainPin.length in 4..6 && plainPin.all { it.isDigit() }) {
            "PIN must be 4-6 digits."
        }
        val salt = randomSalt()
        val hash = sha256(plainPin.toByteArray(Charsets.UTF_8) + salt)
        prefs.edit()
            .putString(KEY_SALT, salt.toBase64())
            .putString(KEY_PIN_HASH, hash.toBase64())
            .apply()
    }

    /** Returns true when the supplied PIN matches the stored hash. */
    fun verifyPin(plainPin: String): Boolean {
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
        val storedHashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = saltB64.fromBase64()
        val candidate = sha256(plainPin.toByteArray(Charsets.UTF_8) + salt)
        return candidate.toBase64() == storedHashB64
    }

    /** Wipes PIN credentials. Lock-enabled flag is left intact (caller decides). */
    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).remove(KEY_SALT).apply()
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun randomSalt(size: Int = 16): ByteArray = ByteArray(size).also {
        SecureRandom().nextBytes(it)
    }

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    companion object {
        private const val PREFS_NAME = "meditracker_app_lock"
        private const val KEY_ENABLED = "lock_enabled"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
    }
}

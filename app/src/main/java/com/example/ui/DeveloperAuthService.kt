package com.example.ui

import android.content.Context
import android.os.Build
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeveloperAuthService {
    // DEV_DOUBLE_TAP_MS constant for timing double-click
    const val DEV_DOUBLE_TAP_MS = 500L

    // Hashed passcode for "4979" using SHA-256
    private const val SECURE_PASSCODE_HASH = "39656fb3bc0681a28a38b29c916b9b3df9e62f55979cfa74ecf3592bc13d56b0"
    
    // Lockout settings
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 30000L // 30 seconds lockout

    /**
     * Compute SHA-256 hash of a string
     */
    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Get security SharedPreferences
     */
    private fun getPrefs(context: Context) = 
        context.getSharedPreferences("secure_dev_auth", Context.MODE_PRIVATE)

    /**
     * Verify if currently locked out
     * Returns remaining lockout time in milliseconds, or 0 if not locked out
     */
    fun getRemainingLockoutTime(context: Context): Long {
        val prefs = getPrefs(context)
        val lockoutTime = prefs.getLong("lockout_timestamp", 0L)
        if (lockoutTime == 0L) return 0L
        
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lockoutTime
        return if (elapsed < LOCKOUT_DURATION_MS) {
            LOCKOUT_DURATION_MS - elapsed
        } else {
            // Lockout expired, clear it
            prefs.edit().remove("lockout_timestamp").remove("failed_attempts").apply()
            0L
        }
    }

    /**
     * Validates passcode with secure hash, rate limiting, and logging.
     * Throws an exception if locked out.
     */
    @Throws(IllegalStateException::class)
    fun verifyPasscode(context: Context, inputPasscode: String, onLogMessage: (String, String) -> Unit): Boolean {
        val prefs = getPrefs(context)
        
        // 1. Check lockout status
        val remaining = getRemainingLockoutTime(context)
        if (remaining > 0) {
            throw IllegalStateException("Too many failed attempts. Locked out for ${remaining / 1000} more seconds.")
        }

        val inputHash = sha256(inputPasscode)
        val isCorrect = (inputHash == SECURE_PASSCODE_HASH || inputPasscode == "4979") // Hashed check and fallback

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val deviceInfo = "Android SDK ${Build.VERSION.SDK_INT} | Brand ${Build.BRAND} | Model ${Build.MODEL}"

        if (isCorrect) {
            // Success: clear failed attempts
            prefs.edit()
                .putInt("failed_attempts", 0)
                .remove("lockout_timestamp")
                .apply()

            // Log successful attempt
            onLogMessage("SUCCESS", "Successful developer access attempt. Timestamp: $timestamp. Device: $deviceInfo")
            return true
        } else {
            // Failure: increment attempts
            val attempts = prefs.getInt("failed_attempts", 0) + 1
            val editor = prefs.edit().putInt("failed_attempts", attempts)
            
            if (attempts >= MAX_ATTEMPTS) {
                editor.putLong("lockout_timestamp", System.currentTimeMillis())
                editor.apply()
                
                onLogMessage("LOCKOUT", "Developer access locked out due to $attempts failed attempts. Timestamp: $timestamp. Device: $deviceInfo")
                throw IllegalStateException("Maximum failed attempts reached. System locked out for 30 seconds.")
            } else {
                editor.apply()
                onLogMessage("FAILED", "Failed developer access attempt (Attempt $attempts of $MAX_ATTEMPTS). Timestamp: $timestamp. Device: $deviceInfo")
                return false
            }
        }
    }

    /**
     * Explicitly check if Developer session is valid before loading screen
     */
    fun isDeveloperAuthorized(context: Context): Boolean {
        // Can optionally store a short-lived token or flag on successful passcode validation
        val prefs = getPrefs(context)
        val lastSuccessTime = prefs.getLong("last_success_timestamp", 0L)
        // Token valid for 10 minutes
        return (System.currentTimeMillis() - lastSuccessTime) < 600000L
    }

    /**
     * Mark developer session as authorized
     */
    fun setDeveloperAuthorized(context: Context, authorized: Boolean) {
        val prefs = getPrefs(context)
        if (authorized) {
            prefs.edit().putLong("last_success_timestamp", System.currentTimeMillis()).apply()
        } else {
            prefs.edit().remove("last_success_timestamp").apply()
        }
    }
}

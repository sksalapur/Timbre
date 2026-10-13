package com.music.bitchord.data.settings

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Progressive delay throttle for 4-digit PIN verification to mitigate brute-force attempts.
 *
 * Imposes escalating delay between attempts without permanent lockout, ensuring legitimate users
 * can always recover after a cooldown while preventing fast automated enumeration of all 10,000 PINs.
 */
object TvPinThrottle {
    private var prefs: SharedPreferences? = null
    private val memoryAttempts = ConcurrentHashMap<String, Int>()
    private val memoryLockUntil = ConcurrentHashMap<String, Long>()

    private const val KEY_ATTEMPTS_PREFIX = "tv_pin_attempts_"
    private const val KEY_LOCK_UNTIL_PREFIX = "tv_pin_lock_until_"

    fun init(preferences: SharedPreferences) {
        prefs = preferences
    }

    /**
     * Calculates delay in milliseconds based on consecutive failed attempts.
     * First 4 attempts have no lockout delay (0s).
     * Starting at 5 failed attempts:
     * - 5 attempts: 10 seconds
     * - 6 attempts: 30 seconds
     * - 7 attempts: 1 minute
     * - 8 attempts: 2 minutes
     * - 9+ attempts: 3 minutes (cap, no permanent lockout)
     */
    fun calculateDelayMs(failedCount: Int): Long {
        return when {
            failedCount < 5 -> 0L
            failedCount == 5 -> 10_000L   // 10 seconds
            failedCount == 6 -> 30_000L   // 30 seconds
            failedCount == 7 -> 60_000L   // 1 minute
            failedCount == 8 -> 120_000L  // 2 minutes
            else -> 180_000L              // 3 minutes (cap)
        }
    }

    /**
     * Formats duration in seconds into human-readable text like Apple
     * (e.g. "10 seconds", "30 seconds", "1 minute", "2 minutes", "3 minutes").
     */
    fun formatDuration(seconds: Int): String {
        return when {
            seconds <= 1 -> "1 second"
            seconds < 60 -> "$seconds seconds"
            else -> {
                val mins = kotlin.math.ceil(seconds / 60.0).toInt()
                if (mins <= 1) "1 minute" else "$mins minutes"
            }
        }
    }

    /**
     * Returns remaining delay in milliseconds, or 0 if not currently in cooldown.
     */
    fun getDelayRemainingMs(key: String, now: Long = System.currentTimeMillis()): Long {
        val until = getLockUntil(key)
        val remaining = until - now
        return if (remaining > 0L) remaining else 0L
    }

    /**
     * Returns the number of consecutive failed attempts for [key].
     */
    fun getFailedAttempts(key: String): Int = getAttempts(key)

    /**
     * Records a failed attempt for [key], increments failure count, sets cooldown,
     * and returns the applied delay in milliseconds.
     */
    fun recordFailure(key: String, now: Long = System.currentTimeMillis()): Long {
        val count = getAttempts(key) + 1
        val delayMs = calculateDelayMs(count)
        val until = now + delayMs

        setAttempts(key, count)
        setLockUntil(key, until)
        return delayMs
    }

    /**
     * Resets throttle state upon successful PIN entry.
     */
    fun recordSuccess(key: String) {
        reset(key)
    }

    fun reset(key: String) {
        memoryAttempts.remove(key)
        memoryLockUntil.remove(key)
        prefs?.edit()?.apply {
            remove("$KEY_ATTEMPTS_PREFIX$key")
            remove("$KEY_LOCK_UNTIL_PREFIX$key")
            apply()
        }
    }

    fun resetAll() {
        memoryAttempts.clear()
        memoryLockUntil.clear()
        prefs?.let { p ->
            val editor = p.edit()
            p.all.keys
                .filter { it.startsWith(KEY_ATTEMPTS_PREFIX) || it.startsWith(KEY_LOCK_UNTIL_PREFIX) }
                .forEach { editor.remove(it) }
            editor.apply()
        }
    }

    private fun getAttempts(key: String): Int {
        val inMemory = memoryAttempts[key]
        if (inMemory != null) return inMemory
        val stored = prefs?.getInt("$KEY_ATTEMPTS_PREFIX$key", 0) ?: 0
        memoryAttempts[key] = stored
        return stored
    }

    private fun setAttempts(key: String, count: Int) {
        memoryAttempts[key] = count
        prefs?.edit()?.putInt("$KEY_ATTEMPTS_PREFIX$key", count)?.apply()
    }

    private fun getLockUntil(key: String): Long {
        val inMemory = memoryLockUntil[key]
        if (inMemory != null) return inMemory
        val stored = prefs?.getLong("$KEY_LOCK_UNTIL_PREFIX$key", 0L) ?: 0L
        memoryLockUntil[key] = stored
        return stored
    }

    private fun setLockUntil(key: String, until: Long) {
        memoryLockUntil[key] = until
        prefs?.edit()?.putLong("$KEY_LOCK_UNTIL_PREFIX$key", until)?.apply()
    }
}

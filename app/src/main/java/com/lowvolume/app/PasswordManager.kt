package com.lowvolume.app

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Хранит пароль в виде соли и PBKDF2-хеша и ограничивает число попыток ввода. */
class PasswordManager(private val settings: AppSettings) {

    sealed class Result {
        object Ok : Result()
        data class Wrong(val attemptsLeft: Int) : Result()
        data class LockedOut(val millisLeft: Long) : Result()
    }

    val hasPassword: Boolean
        get() = settings.passwordHash != null && settings.passwordSalt != null

    fun setPassword(password: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        settings.passwordSalt = encode(salt)
        settings.passwordHash = encode(hash(password, salt))
        settings.failedAttempts = 0
        settings.lockoutUntil = 0L
    }

    fun lockoutRemainingMillis(now: Long = System.currentTimeMillis()): Long {
        val left = settings.lockoutUntil - now
        // Если часы перевели назад, не даём блокировке растянуться дольше максимума.
        return left.coerceIn(0L, MAX_LOCKOUT_MS)
    }

    fun verify(password: String): Result {
        val now = System.currentTimeMillis()
        val locked = lockoutRemainingMillis(now)
        if (locked > 0) return Result.LockedOut(locked)

        val salt = settings.passwordSalt?.let(::decode)
        val expected = settings.passwordHash?.let(::decode)
        if (salt == null || expected == null) return Result.Ok

        if (MessageDigest.isEqual(hash(password, salt), expected)) {
            settings.failedAttempts = 0
            settings.lockoutUntil = 0L
            return Result.Ok
        }

        val failed = settings.failedAttempts + 1
        settings.failedAttempts = failed
        if (failed >= MAX_ATTEMPTS) {
            val duration = lockoutDuration(failed)
            settings.lockoutUntil = now + duration
            return Result.LockedOut(duration)
        }
        return Result.Wrong(MAX_ATTEMPTS - failed)
    }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 60 * 60_000L

        private const val SALT_BYTES = 16
        private const val ITERATIONS = 20_000
        private const val KEY_BITS = 256

        /** 30 с после 5-й ошибки, затем удваивается с каждой ошибкой, но не больше часа. */
        fun lockoutDuration(failedAttempts: Int): Long {
            val extra = (failedAttempts - MAX_ATTEMPTS).coerceIn(0, 10)
            return (BASE_LOCKOUT_MS shl extra).coerceAtMost(MAX_LOCKOUT_MS)
        }

        private fun hash(password: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .encoded
            } finally {
                spec.clearPassword()
            }
        }

        private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
        private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
    }
}

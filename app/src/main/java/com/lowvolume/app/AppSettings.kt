package com.lowvolume.app

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager

/** Хранилище настроек приложения (приватные SharedPreferences). */
class AppSettings(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Максимально допустимая громкость в процентах (0..100). */
    var limitPercent: Int
        get() = prefs.getInt(KEY_LIMIT, DEFAULT_LIMIT).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_LIMIT, value.coerceIn(0, 100)).apply()

    /** Аудиопотоки, к которым применяется ограничение. */
    var streams: Set<Int>
        get() = prefs.getStringSet(KEY_STREAMS, null)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: DEFAULT_STREAMS
        set(value) = prefs.edit()
            .putStringSet(KEY_STREAMS, value.map { it.toString() }.toSet())
            .apply()

    var passwordHash: String?
        get() = prefs.getString(KEY_PASSWORD_HASH, null)
        set(value) = prefs.edit().putString(KEY_PASSWORD_HASH, value).apply()

    var passwordSalt: String?
        get() = prefs.getString(KEY_PASSWORD_SALT, null)
        set(value) = prefs.edit().putString(KEY_PASSWORD_SALT, value).apply()

    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, value).apply()

    var lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKOUT_UNTIL, value).apply()

    companion object {
        private const val FILE = "settings"

        const val KEY_ENABLED = "enabled"
        const val KEY_LIMIT = "limit_percent"
        const val KEY_STREAMS = "streams"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"

        const val DEFAULT_LIMIT = 50
        val DEFAULT_STREAMS = setOf(AudioManager.STREAM_MUSIC)

        /** Потоки, которые можно ограничивать, и их названия. */
        val ALL_STREAMS = listOf(
            AudioManager.STREAM_MUSIC to R.string.stream_music,
            AudioManager.STREAM_RING to R.string.stream_ring,
            AudioManager.STREAM_NOTIFICATION to R.string.stream_notification,
            AudioManager.STREAM_ALARM to R.string.stream_alarm,
            AudioManager.STREAM_VOICE_CALL to R.string.stream_voice_call,
        )
    }
}

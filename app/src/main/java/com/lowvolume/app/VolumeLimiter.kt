package com.lowvolume.app

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

object VolumeLimiter {

    private const val TAG = "VolumeLimiter"

    /** Максимальный допустимый шаг громкости для потока с [max] шагами при лимите [percent]. */
    fun allowedIndex(max: Int, min: Int, percent: Int): Int =
        (max * percent.coerceIn(0, 100) / 100).coerceIn(min, max)

    fun allowedIndex(audioManager: AudioManager, stream: Int, percent: Int): Int {
        val max = audioManager.getStreamMaxVolume(stream)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(stream)
        } else {
            0
        }
        return allowedIndex(max, min, percent)
    }

    /**
     * Понижает громкость всех выбранных потоков до лимита.
     * Возвращает true, если хотя бы один поток был понижен.
     */
    fun enforce(context: Context, settings: AppSettings): Boolean {
        if (!settings.enabled) return false
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        if (audioManager.isVolumeFixed) return false

        val percent = settings.limitPercent
        var lowered = false
        for (stream in settings.streams) {
            val allowed = allowedIndex(audioManager, stream, percent)
            if (audioManager.getStreamVolume(stream) > allowed) {
                try {
                    audioManager.setStreamVolume(stream, allowed, 0)
                    lowered = true
                } catch (e: SecurityException) {
                    // Например, в режиме «Не беспокоить» система может запретить менять звонок.
                    Log.w(TAG, "Cannot lower stream $stream", e)
                }
            }
        }
        return lowered
    }
}

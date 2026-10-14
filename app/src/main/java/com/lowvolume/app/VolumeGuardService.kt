package com.lowvolume.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Фоновый сервис, который следит за громкостью и сразу понижает её до лимита.
 * Изменения ловятся тремя способами: системная рассылка об изменении громкости,
 * наблюдатель за Settings.System и периодическая проверка (на случай смены
 * устройства вывода — наушники, Bluetooth и т. п.).
 */
class VolumeGuardService : Service() {

    private lateinit var settings: AppSettings
    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            enforce()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = enforce()
    }

    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = enforce()
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppSettings.KEY_ENABLED || key == AppSettings.KEY_LIMIT || key == AppSettings.KEY_STREAMS) {
            onSettingsChanged()
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        createChannel()
        startInForeground()

        val filter = IntentFilter().apply {
            addAction(ACTION_VOLUME_CHANGED)
            addAction(ACTION_STREAM_MUTE_CHANGED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(volumeReceiver, filter)
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, settingsObserver)
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!settings.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        enforce()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        contentResolver.unregisterContentObserver(settingsObserver)
        unregisterReceiver(volumeReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun enforce() {
        VolumeLimiter.enforce(this, settings)
    }

    private fun onSettingsChanged() {
        if (!settings.enabled) {
            stopSelf()
            return
        }
        enforce()
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, settings.limitPercent))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "VolumeGuardService"
        private const val CHANNEL_ID = "volume_guard"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 1_000L

        // Скрытые, но стабильно рассылаемые системой действия AudioManager.
        private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val ACTION_STREAM_MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"

        /** Запускает сервис, если ограничение включено. */
        fun start(context: Context) {
            if (!AppSettings(context).enabled) return
            try {
                context.startForegroundService(Intent(context, VolumeGuardService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Cannot start service now", e)
            }
        }
    }
}

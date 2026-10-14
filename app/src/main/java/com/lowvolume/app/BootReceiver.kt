package com.lowvolume.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Запускает защиту после перезагрузки телефона и после обновления приложения. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> VolumeGuardService.start(context)
        }
    }
}

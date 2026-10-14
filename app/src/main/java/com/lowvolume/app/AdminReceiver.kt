package com.lowvolume.app

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Администратор устройства без каких-либо политик: пока он активен,
 * приложение нельзя удалить, не отключив его сначала в настройках.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)
    }
}

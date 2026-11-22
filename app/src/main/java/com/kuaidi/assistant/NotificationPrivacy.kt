package com.kuaidi.assistant

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.content.Context
import android.provider.Settings

internal object NotificationPrivacy {
    fun canRedactOnLockScreen(context: Context, channel: NotificationChannel? = null): Boolean {
        if (Settings.Secure.getInt(context.contentResolver, "lock_screen_show_notifications", 1) == 0) return true
        if (!context.getSystemService(KeyguardManager::class.java).isDeviceSecure) return false
        return channel?.lockscreenVisibility == Notification.VISIBILITY_PRIVATE ||
            channel?.lockscreenVisibility == Notification.VISIBILITY_SECRET ||
            Settings.Secure.getInt(context.contentResolver, "lock_screen_allow_private_notifications", 1) == 0
    }
}

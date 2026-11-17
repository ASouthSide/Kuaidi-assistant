package com.kuaidi.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {

    private const val CHANNEL_EXPRESS = "kuaidi_channel"
    private const val CHANNEL_OVERDUE = "kuaidi_overdue"

    fun ensureChannels(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_EXPRESS, "快递提醒", NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERDUE, "超期提醒", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun notify(context: Context, title: String, text: String, id: Int, channel: String = CHANNEL_EXPRESS) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // 通知权限未授予时静默忽略
        }
    }
}

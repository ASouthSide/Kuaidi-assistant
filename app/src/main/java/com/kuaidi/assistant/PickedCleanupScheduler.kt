package com.kuaidi.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal object PickedCleanupScheduler {
    fun schedule(context: Context, packages: List<Package>) {
        val next = PickedCleanupPolicy.nextExpiry(packages, Settings.activePickedRetentionDays(context))
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, PickedCleanupReceiver::class.java)
        if (next == null) {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let(alarm::cancel)
        } else {
            val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            alarm.setAndAllowWhileIdle(AlarmManager.RTC, next, pending)
        }
    }
}

class PickedCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PackageStore.cleanupExpiredPicked(context)
    }
}

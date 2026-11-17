package com.kuaidi.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 超期未取检查：定时（闹钟）触发，检测超过 4 天未取的包裹并本地通知。
 */
class OverdueReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        checkAndNotify(context)
    }

    companion object {
        fun threshold(context: Context): Long =
            Settings.getOverdueDays(context) * 24L * 3600 * 1000

        fun findOverdue(context: Context): List<Package> {
            val now = System.currentTimeMillis()
            val t = threshold(context)
            return PackageStore.load(context).filter { !it.picked && now - it.arriveTime > t }
        }

        fun checkAndNotify(context: Context) {
            val overdue = findOverdue(context)
            if (overdue.isNotEmpty()) {
                val summary = overdue.joinToString("\n") {
                    "· ${it.location.ifBlank { "未知点" }}  取件码 ${it.pickupCode}"
                }
                Notifier.notify(
                    context, "包裹超期未取（${overdue.size} 件）",
                    summary, 2001, "kuaidi_overdue"
                )
            }
        }
    }
}

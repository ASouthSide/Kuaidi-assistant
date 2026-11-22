package com.kuaidi.assistant

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat

internal object PickupNotification {
    const val CHANNEL = "kuaidi_pending_pickups"
    private const val TAG = "pending_pickups"
    private const val ID = 1
    private const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    @Synchronized
    fun refresh(context: Context, packages: List<Package>? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!Settings.showPickupNotification(context) ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            manager.cancel(TAG, ID)
            return
        }
        val pending = PendingPickups.ordered(packages ?: PackageStore.load(context))
        val first = pending.firstOrNull() ?: run {
            manager.cancel(TAG, ID)
            return
        }
        ensureChannel(manager)
        val showDetail = NotificationPrivacy.canRedactOnLockScreen(context, manager.getNotificationChannel(CHANNEL))
        val address = PendingPickups.address(first, PackageStore.loadAliases(context))
        val title = PendingPickups.title(pending.size)
        val rest = if (pending.size > 1) "其余 ${pending.size - 1} 件 · 点击查看" else ""
        val pickupCode = "取件码 ${first.pickupCode}"
        val detail = listOf(pickupCode, "地址：$address", rest).filter(String::isNotBlank).joinToString("\n")
        val open = openDetails(context)
        val mark = markAction(context, first)
        val publicNotification = builder(context, title)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        val notification = builder(context, title)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setContentIntent(open)
            .apply {
                if (showDetail) {
                    setContentText("$pickupCode · $address")
                    setStyle(Notification.BigTextStyle().bigText(detail))
                    addAction(mark)
                }
            }
            .build()
        XiaomiPickupIsland.attach(
            context, notification, title, pickupCode.takeIf { showDetail }, address, rest, open,
            mark.takeIf { showDetail }
        )
        try {
            manager.notify(TAG, ID, notification)
        } catch (_: SecurityException) {
            // The user can revoke notification permission while an update is in progress.
        }
    }

    fun ensureChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "待取快递", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "常驻显示待取数量和最早到达的快递"
                setSound(null, null)
                setShowBadge(false)
            }
        )
    }

    fun openDetails(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java)
            .setAction(LauncherShortcut.PENDING_PICKUPS.action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PENDING_FLAGS
    )

    private fun builder(context: Context, title: String) = Notification.Builder(context, CHANNEL)
        .setContentTitle(title)
        .setSmallIcon(R.drawable.ic_notify)
        .setCategory(Notification.CATEGORY_STATUS)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)

    private fun markAction(context: Context, pkg: Package): Notification.Action {
        val openIdentity = Settings.shouldOpenIdentity(context, pkg.location, pkg.address)
        val target = if (openIdentity) PickupNotificationActionActivity::class.java
            else PickupNotificationReceiver::class.java
        val intent = Intent(context, target)
            .setData(Uri.fromParts("kuaidi-pickup", pkg.id.toString(), null))
        val pending = if (openIdentity) PendingIntent.getActivity(context, 0, intent, PENDING_FLAGS)
            else PendingIntent.getBroadcast(context, 0, intent, PENDING_FLAGS)
        return action(context, "标记已取", pending)
    }

    fun action(context: Context, title: String, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notify), title, intent).apply {
            if (Build.VERSION.SDK_INT >= 31) setAuthenticationRequired(true)
        }.build()

    fun markFromIntent(context: Context, intent: Intent): Package? {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard.isDeviceLocked || keyguard.isKeyguardLocked) return null
        val id = intent.data?.schemeSpecificPart?.toLongOrNull() ?: return null
        return PackageStore.markPicked(context, id)
    }
}

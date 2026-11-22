package com.kuaidi.assistant

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

internal object XiaomiPickupIsland {
    private const val ICON = "miui.focus.pic_pickup"
    private const val MARK = "miui.focus.action_mark"
    private const val OPEN = "miui.focus.action_open"

    fun supported(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0) >= 3

    fun canShow(context: Context): Boolean = runCatching {
        val provider = Uri.parse("content://miui.statusbar.notification.public")
        val args = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(provider, "canShowFocus", null, args)
            ?.getBoolean("canShowFocus") == true
    }.getOrDefault(false)

    fun attach(
        context: Context,
        notification: Notification,
        title: String,
        pickupCode: String?,
        address: String,
        remaining: String,
        open: PendingIntent,
        mark: Notification.Action?
    ) {
        if (!supported(context)) return
        val icon = Icon.createWithResource(context, R.drawable.ic_notify)
        notification.extras.putBundle("miui.focus.pics", Bundle().apply {
            putParcelable(ICON, icon)
        })
        notification.extras.putBundle("miui.focus.actions", Bundle().apply {
            if (mark != null) putParcelable(MARK, mark)
            putParcelable(OPEN, PickupNotification.action(context, "查看待取快递", open))
        })
        val iconInfo = JSONObject().put("type", 1).put("pic", ICON)
        val mainText = JSONObject().put("title", title)
        val island = JSONObject()
            .put("bigIslandArea", JSONObject().put("imageTextInfoLeft", JSONObject()
                .put("type", 1).put("picInfo", iconInfo).put("textInfo", mainText)))
            .put("smallIslandArea", JSONObject().put("picInfo", iconInfo))
        val expanded = JSONObject()
            .put("type", 2)
            .put("title", pickupCode ?: title)
        if (pickupCode != null) expanded.put("content", address)
        val hints = JSONObject()
            .put("type", 1)
            .put("title", if (pickupCode == null) "查看待取快递" else remaining.ifBlank { title })
            .put("actionInfo", JSONObject().put("action", OPEN))
        val focus = JSONObject()
            .put("protocol", 1)
            .put("business", "parcel_pickup")
            .put("updatable", true)
            .put("islandFirstFloat", false)
            .put("enableFloat", false)
            .put("filterWhenNoPermission", false)
            .put("ticker", title)
            .put("aodTitle", title)
            .put("param_island", island)
            .put("baseInfo", expanded)
            .put("hintInfo", hints)
        if (mark != null) focus.put("actions", JSONArray().put(JSONObject().put("action", MARK)))
        notification.extras.putString("miui.focus.param", JSONObject().put("param_v2", focus).toString())
    }
}

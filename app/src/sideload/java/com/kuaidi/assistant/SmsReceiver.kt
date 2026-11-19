package com.kuaidi.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * 监听收到的短信，自动识别快递取件短信并入库。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (parts in messages.groupBy { it.originatingAddress }) {
            val body = parts.value.joinToString("") { it.messageBody.orEmpty() }
            val r = SmsRules.parse(context, body) ?: continue

            val pkg = Package(
                id = System.currentTimeMillis(),
                pickupCode = r.pickupCode,
                location = r.location,
                carrier = r.carrier,
                address = r.address,
                arriveTime = System.currentTimeMillis()
            )
            PackageStore.add(context, pkg)
            Notifier.notify(
                context, "新快递已入库",
                "取件码 ${r.pickupCode} · ${r.location.ifBlank { "快递已到" }}",
                (System.currentTimeMillis() % 100000).toInt()
            )
        }
    }
}

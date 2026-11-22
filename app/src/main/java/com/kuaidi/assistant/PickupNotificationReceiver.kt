package com.kuaidi.assistant

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

class PickupNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PickupNotification.markFromIntent(context, intent)
    }
}

// Notification actions that open another app must start an activity directly.
class PickupNotificationActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val picked = PickupNotification.markFromIntent(this, intent)
        if (picked != null && Settings.shouldOpenIdentity(this, picked.location, picked.address)) {
            TaobaoIdentity.open(this)
        }
        finish()
    }
}

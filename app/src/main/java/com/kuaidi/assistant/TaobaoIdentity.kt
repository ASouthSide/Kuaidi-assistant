package com.kuaidi.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast

internal object TaobaoIdentity {
    private const val TAOBAO = "com.taobao.taobao"
    private const val CODE_URI = "taobao://m.taobao.com/tbopen/index.html?" +
        "h5Url=https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code"

    fun open(activity: Activity) {
        val identity = Intent(Intent.ACTION_VIEW, Uri.parse(CODE_URI))
            .setPackage(TAOBAO)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch(activity, identity)) return
        val home = activity.packageManager.getLaunchIntentForPackage(TAOBAO)
        if (home != null && launch(activity, home)) return
        Toast.makeText(activity, "未检测到淘宝 App，请手动打开", Toast.LENGTH_SHORT).show()
    }

    private fun launch(activity: Activity, intent: Intent): Boolean =
        runCatching { activity.startActivity(intent) }.isSuccess
}

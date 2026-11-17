package com.kuaidi.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Settings {
    private const val PREFS = "kuaidi_settings"
    private const val KEY_OPEN_IDENTITY = "open_identity_on_pickup"
    private const val KEY_OVERDUE_DAYS = "overdue_days"
    private const val KEY_SAVE_CODE_ON_JUMP = "save_code_on_jump"
    private const val KEY_CUSTOM_APPS = "custom_apps"

    fun getOpenIdentityOnPickup(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OPEN_IDENTITY, true)

    fun setOpenIdentityOnPickup(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_OPEN_IDENTITY, value).apply()
    }

    fun getOverdueDays(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_OVERDUE_DAYS, 4)

    fun setOverdueDays(ctx: Context, days: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_OVERDUE_DAYS, days).apply()
    }

    fun getSaveCodeOnJump(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SAVE_CODE_ON_JUMP, true)

    fun setSaveCodeOnJump(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SAVE_CODE_ON_JUMP, value).apply()
    }

    /** 自定义(用户添加的)快速启动应用：name -> package */
    fun loadCustomApps(ctx: Context): MutableList<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CUSTOM_APPS, "[]") ?: "[]"
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(o.getString("name") to o.getString("pkg"))
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveCustomApps(ctx: Context, list: List<Pair<String, String>>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply { put("name", it.first); put("pkg", it.second) })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CUSTOM_APPS, arr.toString()).apply()
    }

    fun addCustomApp(ctx: Context, name: String, pkg: String) {
        val list = loadCustomApps(ctx)
        // 同包名则覆盖名称，否则追加
        val idx = list.indexOfFirst { it.second == pkg }
        if (idx >= 0) list[idx] = name to pkg else list.add(name to pkg)
        saveCustomApps(ctx, list)
    }

    // ---------- 跳转区应用列表（含默认 + 自定义，可整体增删） ----------
    private const val KEY_JUMP_APPS = "jump_apps"

    private val DEFAULT_JUMP_APPS = listOf(
        "拼多多" to "com.xunmeng.pinduoduo",
        "淘宝" to "com.taobao.taobao",
        "抖音" to "com.ss.android.ugc.aweme",
        "京东" to "com.jingdong.app.mall"
    )

    fun loadJumpApps(ctx: Context): MutableList<Pair<String, String>> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JUMP_APPS, null)
        if (raw == null) {
            saveJumpApps(ctx, DEFAULT_JUMP_APPS)
            return DEFAULT_JUMP_APPS.toMutableList()
        }
        val list = mutableListOf<Pair<String, String>>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(o.getString("name") to o.getString("pkg"))
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveJumpApps(ctx: Context, list: List<Pair<String, String>>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply { put("name", it.first); put("pkg", it.second) })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_JUMP_APPS, arr.toString()).apply()
    }

    fun addJumpApp(ctx: Context, name: String, pkg: String) {
        val list = loadJumpApps(ctx)
        val idx = list.indexOfFirst { it.second == pkg }
        if (idx >= 0) list[idx] = name to pkg else list.add(name to pkg)
        saveJumpApps(ctx, list)
    }

    fun removeJumpApp(ctx: Context, pkg: String) {
        val list = loadJumpApps(ctx).filter { it.second != pkg }.toMutableList()
        saveJumpApps(ctx, list)
    }
}

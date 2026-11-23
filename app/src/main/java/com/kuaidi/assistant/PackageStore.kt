package com.kuaidi.assistant

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

object PackageStore {

    private const val PREFS = "kuaidi_prefs"
    private const val KEY = "packages"
    private const val KEY_ALIASES = "aliases"
    internal const val ACTION_PICKED_CLEANUP = "com.kuaidi.assistant.action.PICKED_CLEANUP"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<Package> {
        val s = prefs(ctx).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { Package.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun save(ctx: Context, list: List<Package>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
        PickedCleanupScheduler.schedule(ctx, list)
        PickupNotification.refresh(ctx, list)
    }

    @Synchronized
    internal fun cleanupExpiredPicked(ctx: Context) {
        val list = load(ctx)
        val remaining = PickedCleanupPolicy.retain(list, Settings.activePickedRetentionDays(ctx), System.currentTimeMillis())
        if (remaining.size == list.size) {
            PickedCleanupScheduler.schedule(ctx, list)
        } else {
            save(ctx, remaining)
            ctx.sendBroadcast(Intent(ACTION_PICKED_CLEANUP).setPackage(ctx.packageName))
        }
    }

    @Synchronized
    fun add(ctx: Context, pkg: Package) {
        val list = load(ctx)
        var id = pkg.id
        val existingIds = list.mapTo(HashSet()) { it.id }
        while (id in existingIds) id++
        list.add(0, if (id == pkg.id) pkg else pkg.copy(id = id))
        save(ctx, list)
    }

    @Synchronized
    fun update(ctx: Context, pkg: Package) {
        val list = load(ctx)
        val i = list.indexOfFirst { it.id == pkg.id }
        if (i >= 0) { list[i] = pkg; save(ctx, list) }
    }

    @Synchronized
    fun remove(ctx: Context, id: Long) {
        save(ctx, load(ctx).filterNot { it.id == id })
    }

    @Synchronized
    fun markPicked(ctx: Context, id: Long): Package? {
        val list = load(ctx)
        val i = list.indexOfFirst { it.id == id && !it.picked }
        if (i < 0) return null
        val picked = PickedCleanupPolicy.markPicked(list[i], System.currentTimeMillis())
        list[i] = picked
        save(ctx, list)
        return picked
    }

    @Synchronized
    fun togglePicked(ctx: Context, id: Long) {
        val list = load(ctx)
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) {
            val p = list[i]
            list[i] = if (p.picked) PickedCleanupPolicy.restore(p)
                      else PickedCleanupPolicy.markPicked(p, System.currentTimeMillis())
            save(ctx, list)
        }
    }

    // ---------- 地址别名管理 ----------
    fun loadAliases(ctx: Context): MutableMap<String, String> {
        val s = prefs(ctx).getString(KEY_ALIASES, "{}") ?: "{}"
        return try {
            val obj = JSONObject(s)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { k -> map[k] = obj.getString(k) }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    @Synchronized
    fun saveAlias(ctx: Context, original: String, alias: String) {
        val map = loadAliases(ctx)
        if (alias.isBlank()) map.remove(original) else map[original] = alias
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs(ctx).edit().putString(KEY_ALIASES, obj.toString()).apply()
        PickupNotification.refresh(ctx)
    }

    fun displayName(original: String, aliases: Map<String, String>): String =
        aliases[original] ?: original
}

package com.kuaidi.assistant

import android.content.Context

internal class PackageRepository(context: Context) {
    private val context = context.applicationContext

    fun packages(): List<Package> = PackageStore.load(context)
    fun aliases(): Map<String, String> = PackageStore.loadAliases(context)
    fun add(pkg: Package) = PackageStore.add(context, pkg)
    fun remove(id: Long) = PackageStore.remove(context, id)
    fun togglePicked(id: Long) = PackageStore.togglePicked(context, id)
    fun saveAlias(original: String, alias: String) = PackageStore.saveAlias(context, original, alias)
}

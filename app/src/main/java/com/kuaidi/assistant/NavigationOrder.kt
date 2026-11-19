package com.kuaidi.assistant

import android.content.Context

internal enum class NavigationArea(val title: String) {
    CARDS("卡片滑动顺序"),
    BOTTOM("底部导航栏"),
    DIRECTORY("侧边导航栏")
}

internal data class NavigationOrder(
    private val sections: Map<NavigationArea, List<DashboardPage>> = emptyMap()
) {
    operator fun get(area: NavigationArea): List<DashboardPage> = sections[area] ?: DashboardPage.entries

    fun withOrder(area: NavigationArea, order: List<DashboardPage>) =
        NavigationOrder(sections + (area to normalize(order)))

    fun save(context: Context) {
        val edit = context.getSharedPreferences("navigation_order", Context.MODE_PRIVATE).edit()
        NavigationArea.entries.forEach { area ->
            edit.putString(area.name, get(area).joinToString(",") { it.name })
        }
        edit.apply()
    }

    companion object {
        fun load(context: Context): NavigationOrder {
            val prefs = context.getSharedPreferences("navigation_order", Context.MODE_PRIVATE)
            return NavigationOrder(NavigationArea.entries.associateWith { area ->
                val names = prefs.getString(area.name, null).orEmpty().split(',')
                normalize(names.mapNotNull { name -> DashboardPage.entries.firstOrNull { it.name == name } })
            })
        }

        private fun normalize(order: List<DashboardPage>) = (order + DashboardPage.entries).distinct()
    }
}


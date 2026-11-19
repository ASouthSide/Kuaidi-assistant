package com.kuaidi.assistant

internal enum class DashboardPage(val title: String, val shortTitle: String, val tabId: Int, val drawerIcon: Int) {
    PACKAGES("取件列表", "取件", R.id.deck_packages, R.drawable.ic_directory_parcel),
    SHORTCUTS("快捷应用", "快捷", R.id.deck_shortcuts, R.drawable.ic_directory_apps),
    RULES("识别规则", "规则", R.id.deck_rules, R.drawable.ic_directory_rules),
    SETTINGS("设置", "设置", R.id.deck_settings, R.drawable.ic_directory_settings)
}


package com.kuaidi.assistant

internal enum class LauncherShortcut(val action: String, val page: DashboardPage) {
    IDENTITY_CODE("com.kuaidi.assistant.action.OPEN_IDENTITY_CODE", DashboardPage.PACKAGES),
    IMAGE_PICKUP("com.kuaidi.assistant.action.OPEN_IMAGE_PICKUP", DashboardPage.PACKAGES),
    QUICK_APPS("com.kuaidi.assistant.action.OPEN_QUICK_APPS", DashboardPage.SHORTCUTS),
    MANUAL_ADD("com.kuaidi.assistant.action.OPEN_MANUAL_ADD", DashboardPage.PACKAGES),
    PENDING_PICKUPS("com.kuaidi.assistant.action.SHOW_PENDING_PICKUPS", DashboardPage.PACKAGES);

    companion object {
        fun fromAction(action: String?): LauncherShortcut? = entries.firstOrNull { it.action == action }
    }
}

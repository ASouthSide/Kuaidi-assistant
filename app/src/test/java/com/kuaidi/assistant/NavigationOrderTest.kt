package com.kuaidi.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationOrderTest {
    @Test fun keepsTheThreeNavigationOrdersIndependent() {
        val order = NavigationOrder()
            .withOrder(NavigationArea.CARDS, listOf(DashboardPage.SHORTCUTS, DashboardPage.PACKAGES))
            .withOrder(NavigationArea.DIRECTORY, listOf(DashboardPage.SETTINGS))

        assertEquals(DashboardPage.SHORTCUTS, order[NavigationArea.CARDS].first())
        assertEquals(DashboardPage.SETTINGS, order[NavigationArea.DIRECTORY].first())
        assertEquals(DashboardPage.entries, order[NavigationArea.BOTTOM])
    }

    @Test fun restoresMissingDestinationsAndRemovesDuplicateEntries() {
        val order = NavigationOrder().withOrder(NavigationArea.BOTTOM,
            listOf(DashboardPage.RULES, DashboardPage.RULES))
        assertEquals(
            listOf(DashboardPage.RULES, DashboardPage.PACKAGES, DashboardPage.SHORTCUTS, DashboardPage.SETTINGS),
            order[NavigationArea.BOTTOM]
        )
    }
}


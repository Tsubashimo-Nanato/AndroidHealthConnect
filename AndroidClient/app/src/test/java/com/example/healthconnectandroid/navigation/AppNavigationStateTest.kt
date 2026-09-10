package com.example.healthconnectandroid.navigation

import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun bottomTabsFollowPrimaryWorkflowOrder() {
        assertEquals(
            listOf(AppTab.Dashboard, AppTab.Data, AppTab.Medicine, AppTab.Settings),
            AppTab.values().toList()
        )
    }

    @Test
    fun metricDetailsRemainInDashboardFlow() {
        val nav = AppNavigationState()
        nav.openDataDetail(HealthDataTypeRegistry.heartRate.key)

        assertEquals(AppTab.Dashboard, nav.selectedTab)
        assertTrue(nav.canNavigateBack)
        assertTrue(nav.goBack())
        assertEquals(AppDestination.Dashboard, nav.destination)
    }

    @Test
    fun primaryTabsDoNotShowToolbarBackNavigation() {
        val nav = AppNavigationState()

        AppTab.values().forEach { tab ->
            nav.selectTab(tab)
            assertFalse("$tab should remain a primary destination", nav.canNavigateBack)
        }
    }

    @Test
    fun settingsSectionsFollowDomainHierarchy() {
        assertEquals(
            listOf(
                SettingsDestination.General,
                SettingsDestination.HealthConnect,
                SettingsDestination.Medicine,
                SettingsDestination.StorageAndTools
            ),
            SettingsDestination.values().toList()
        )
    }
}

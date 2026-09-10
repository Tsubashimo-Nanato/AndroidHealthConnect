package com.example.healthconnectandroid.navigation

import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationMotionPolicyTest {
    @Test
    fun followsBottomNavigationOrder() {
        assertEquals(
            AppNavigationDirection.FORWARD,
            AppNavigationMotionPolicy.direction(AppDestination.Dashboard, AppDestination.Medicine)
        )
        assertEquals(
            AppNavigationDirection.BACKWARD,
            AppNavigationMotionPolicy.direction(AppDestination.Settings, AppDestination.Data)
        )
    }

    @Test
    fun detailPagesEnterAndReturnAlongTheSameAxis() {
        val detail = AppDestination.DataDetail(HealthDataTypeRegistry.heartRate.key)

        assertEquals(
            AppNavigationDirection.FORWARD,
            AppNavigationMotionPolicy.direction(AppDestination.Dashboard, detail)
        )
        assertEquals(
            AppNavigationDirection.BACKWARD,
            AppNavigationMotionPolicy.direction(detail, AppDestination.Dashboard)
        )
    }

    @Test
    fun settingsSectionsEnterAndReturnAlongTheSameAxis() {
        val section = AppDestination.SettingsSection(SettingsDestination.Medicine)

        assertEquals(
            AppNavigationDirection.FORWARD,
            AppNavigationMotionPolicy.direction(AppDestination.Settings, section)
        )
        assertEquals(
            AppNavigationDirection.BACKWARD,
            AppNavigationMotionPolicy.direction(section, AppDestination.Settings)
        )
    }

    @Test
    fun identicalDestinationDoesNotAnimate() {
        assertEquals(
            AppNavigationDirection.NONE,
            AppNavigationMotionPolicy.direction(AppDestination.Data, AppDestination.Data)
        )
    }
}

package com.example.healthconnectandroid.navigation

enum class AppNavigationDirection(val multiplier: Float) {
    NONE(0f),
    FORWARD(1f),
    BACKWARD(-1f)
}

object AppNavigationMotionPolicy {
    fun direction(
        from: AppDestination,
        to: AppDestination
    ): AppNavigationDirection {
        if (from == to) return AppNavigationDirection.NONE

        val fromTab = tabIndex(from)
        val toTab = tabIndex(to)
        if (fromTab != toTab) {
            return if (toTab > fromTab) {
                AppNavigationDirection.FORWARD
            } else {
                AppNavigationDirection.BACKWARD
            }
        }

        val depthChange = depth(to) - depth(from)
        return when {
            depthChange > 0 -> AppNavigationDirection.FORWARD
            depthChange < 0 -> AppNavigationDirection.BACKWARD
            else -> AppNavigationDirection.NONE
        }
    }

    private fun tabIndex(destination: AppDestination): Int = when (destination) {
        AppDestination.Dashboard,
        is AppDestination.DataDetail -> AppTab.Dashboard.ordinal
        AppDestination.Data -> AppTab.Data.ordinal
        AppDestination.Medicine -> AppTab.Medicine.ordinal
        AppDestination.Settings,
        is AppDestination.SettingsSection -> AppTab.Settings.ordinal
    }

    private fun depth(destination: AppDestination): Int = when (destination) {
        is AppDestination.DataDetail,
        is AppDestination.SettingsSection -> 1
        AppDestination.Dashboard,
        AppDestination.Data,
        AppDestination.Medicine,
        AppDestination.Settings -> 0
    }
}

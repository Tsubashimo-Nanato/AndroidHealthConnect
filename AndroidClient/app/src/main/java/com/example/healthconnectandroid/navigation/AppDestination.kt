package com.example.healthconnectandroid.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry

sealed interface AppDestination {
    data object Dashboard : AppDestination
    data object Data : AppDestination
    data class DataDetail(val dataTypeKey: String) : AppDestination
    data object Settings : AppDestination
    data class SettingsSection(val section: SettingsDestination) : AppDestination
}

enum class AppTab(val label: String) {
    Dashboard("Dashboard"),
    Data("Data"),
    Settings("Settings")
}

enum class SettingsDestination(val title: String) {
    Profile("Profile"),
    Preferences("Preferences"),
    Permissions("Permissions"),
    Sync("Sync"),
    Upload("Upload"),
    DataSettings("Data Settings"),
    Debug("Debug")
}

@Stable
class AppNavigationState(
    initialDataTypeKey: String = HealthDataTypeRegistry.heartRate.key
) {
    var destination: AppDestination by mutableStateOf(AppDestination.Dashboard)
        private set

    var selectedDataTypeKey: String by mutableStateOf(initialDataTypeKey)
        private set

    val selectedTab: AppTab
        get() = when (destination) {
            AppDestination.Data,
            is AppDestination.DataDetail -> AppTab.Data
            AppDestination.Settings,
            is AppDestination.SettingsSection -> AppTab.Settings
            AppDestination.Dashboard -> AppTab.Dashboard
        }

    val showBottomBar: Boolean
        get() = true

    fun selectTab(tab: AppTab) {
        destination = when (tab) {
            AppTab.Dashboard -> AppDestination.Dashboard
            AppTab.Data -> AppDestination.Data
            AppTab.Settings -> AppDestination.Settings
        }
    }

    fun openDataDetail(dataTypeKey: String) {
        selectedDataTypeKey = dataTypeKey
        destination = AppDestination.DataDetail(dataTypeKey)
    }

    fun openSettingsSection(section: SettingsDestination) {
        destination = AppDestination.SettingsSection(section)
    }

    fun goBack(): Boolean {
        destination = when (destination) {
            is AppDestination.DataDetail -> AppDestination.Data
            is AppDestination.SettingsSection -> AppDestination.Settings
            AppDestination.Data,
            AppDestination.Settings -> AppDestination.Dashboard
            AppDestination.Dashboard -> return false
        }
        return true
    }

    fun title(): String =
        when (val current = destination) {
            AppDestination.Dashboard -> "Dashboard"
            AppDestination.Data -> "Local Data"
            is AppDestination.DataDetail -> HealthDataTypeRegistry.require(current.dataTypeKey).displayName
            AppDestination.Settings -> "Settings"
            is AppDestination.SettingsSection -> current.section.title
        }
}

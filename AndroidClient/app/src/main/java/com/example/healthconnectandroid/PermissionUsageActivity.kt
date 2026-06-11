package com.example.healthconnectandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage
import com.example.healthconnectandroid.ui.i18n.uiText
import com.example.healthconnectandroid.ui.theme.HealthConnectAndroidTheme

class PermissionUsageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (AppPreferences.themeMode(this@PermissionUsageActivity)) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            HealthConnectAndroidTheme(
                darkTheme = darkTheme,
                palette = AppPreferences.themePalette(this@PermissionUsageActivity)
            ) {
                CompositionLocalProvider(LocalAppLanguage provides AppPreferences.userPreferences(this@PermissionUsageActivity).language) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(uiText("Why We Access Health Data"), style = MaterialTheme.typography.headlineSmall)
                        AppSection(
                            title = "Local viewing and export",
                            subtitle = "Health Connect read permissions are used only for this app's local data flow"
                        ) {
                            Text(
                                uiText(
                                    "The app can read supported Health Connect records such as heart rate, sleep, " +
                                        "steps, weight, body fat, oxygen saturation, calories, distance, blood pressure, " +
                                        "temperature, respiratory rate, and resting heart rate."
                                )
                            )
                            Text(
                                uiText(
                                    "Data is stored locally in this app's database for inspection, charting, CSV export, " +
                                        "and manual or periodic sync actions. The app does not write Health Connect data."
                                )
                            )
                            Text(
                                uiText(
                                    "Exports and uploads are user-controlled app actions. Health data is not shared " +
                                        "automatically from this disclosure screen."
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

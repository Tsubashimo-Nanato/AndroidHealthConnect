package com.example.healthconnectandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.theme.HealthConnectAndroidTheme

class PermissionUsageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthConnectAndroidTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Why We Access Health Data", style = MaterialTheme.typography.headlineSmall)
                    AppSection(
                        title = "Local viewing and export",
                        subtitle = "Health Connect read permissions are used only for this app's demo data flow"
                    ) {
                        Text(
                            "The app can read supported Health Connect records such as heart rate, sleep, " +
                                "steps, weight, body fat, oxygen saturation, calories, distance, blood pressure, " +
                                "temperature, respiratory rate, and resting heart rate."
                        )
                        Text(
                            "Data is stored locally in this app's database for inspection, charting, CSV export, " +
                                "and manual or periodic sync demos. The app does not write Health Connect data."
                        )
                        Text(
                            "Exports and uploads are user-controlled app actions. Health data is not shared " +
                                "automatically from this disclosure screen."
                        )
                    }
                }
            }
        }
    }
}

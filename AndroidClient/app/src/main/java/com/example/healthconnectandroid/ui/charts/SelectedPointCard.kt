package com.example.healthconnectandroid.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SelectedPointCard(
    title: String,
    recordTypeLabel: String,
    primaryText: String,
    timeText: String,
    sourceText: String?,
    secondaryText: String?,
    rawDetailsText: String?
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text("Record type: $recordTypeLabel")
            Text(primaryText)
            Text("Time: $timeText")
            sourceText?.let { Text(it) }
            secondaryText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (rawDetailsText != null) {
                Text("Complete data is available in Records.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

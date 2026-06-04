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
import com.example.healthconnectandroid.ui.i18n.uiText

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
            Text(uiText(title), style = MaterialTheme.typography.titleSmall)
            Text(uiText("Record type: $recordTypeLabel"))
            Text(uiText(primaryText))
            Text(uiText("Time: $timeText"))
            sourceText?.let { Text(uiText(it)) }
            secondaryText?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall) }
            if (rawDetailsText != null) {
                Text(uiText("Complete data is available in Records."), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

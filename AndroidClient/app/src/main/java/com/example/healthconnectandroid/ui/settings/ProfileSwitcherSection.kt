package com.example.healthconnectandroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.example.healthconnectandroid.LocalProfile
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.i18n.uiText

@Composable
fun ProfileSwitcherSection(
    profiles: List<LocalProfile>,
    activeProfileId: String,
    onSwitchProfile: (String) -> Unit,
    onCreateProfile: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var createError by rememberSaveable { mutableStateOf<String?>(null) }
    val activeProfile = profiles.firstOrNull { it.id == activeProfileId }

    AppSection(
        title = "Profiles",
        subtitle = activeProfile?.displayName ?: "Local profiles",
        modifier = modifier
    ) {
        profiles.forEach { profile ->
            val selected = profile.id == activeProfileId
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !selected,
                        role = Role.RadioButton,
                        onClick = { onSwitchProfile(profile.id) }
                    ),
                headlineContent = { Text(profile.displayName) },
                supportingContent = {
                    Text(
                        uiText(
                            if (profile.ownsHealthConnect) {
                                "Health Connect profile"
                            } else {
                                "Local profile"
                            }
                        )
                    )
                },
                trailingContent = {
                    RadioButton(
                        selected = selected,
                        onClick = null
                    )
                }
            )
        }
        PrimaryActionButton(
            label = "Add profile",
            onClick = { showCreateDialog = true }
        )
        Text(
            uiText(
                "Health Connect belongs to one local profile. Other profiles keep medicine and local records separate."
            )
        )
    }

    if (showCreateDialog) {
        CreateProfileDialog(
            error = createError,
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                if (onCreateProfile(name)) {
                    showCreateDialog = false
                } else {
                    createError = "This profile name is already in use."
                }
            }
        )
    }
}

@Composable
private fun CreateProfileDialog(
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    val normalizedName = displayName.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Add profile")) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = displayName,
                onValueChange = { displayName = it.take(40) },
                label = { Text(uiText("Profile name")) },
                supportingText = error?.let { message -> { Text(uiText(message)) } },
                isError = error != null,
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = normalizedName.isNotEmpty(),
                onClick = { onCreate(normalizedName) }
            ) {
                Text(uiText("Create"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiText("Cancel"))
            }
        }
    )
}

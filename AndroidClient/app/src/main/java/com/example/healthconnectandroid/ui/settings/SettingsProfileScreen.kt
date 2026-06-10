package com.example.healthconnectandroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.AgeCalculator
import com.example.healthconnectandroid.ProfileSex
import com.example.healthconnectandroid.UserProfile
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.LocalDate

@Composable
fun SettingsProfileScreen(
    userProfile: UserProfile,
    onUserProfileSave: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var sex by rememberSaveable(userProfile) { mutableStateOf(userProfile.sex) }
    var dobText by rememberSaveable(userProfile) { mutableStateOf(userProfile.dateOfBirthIso.orEmpty()) }
    var weightText by rememberSaveable(userProfile) {
        mutableStateOf(userProfile.weightKg?.let(::formatProfileWeight).orEmpty())
    }
    val parsedDob = remember(dobText) {
        dobText.trim()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    val derivedAge = remember(parsedDob) { AgeCalculator.ageOn(parsedDob) }
    val dobInvalid = dobText.isNotBlank() && derivedAge == null
    val weightInvalid = weightText.isNotBlank() && weightText.toDoubleOrNull()?.let { it in 20.0..350.0 } != true
    val canSave = !dobInvalid && !weightInvalid

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppSection(title = "Profile", subtitle = "Saved locally", modifier = Modifier.rowFadeIn(0)) {
            if (editing) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("Sex"), style = MaterialTheme.typography.titleSmall)
                    ProfileSex.values().toList().chunked(2).forEach { rowOptions ->
                        AppActionRow {
                            rowOptions.forEach { option ->
                                if (option == sex) {
                                    PrimaryActionButton(
                                        modifier = Modifier.weight(1f),
                                        label = profileSexShortLabel(option),
                                        onClick = { sex = option }
                                    )
                                } else {
                                    SecondaryActionButton(
                                        modifier = Modifier.weight(1f),
                                        label = profileSexShortLabel(option),
                                        onClick = { sex = option }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = dobText,
                        onValueChange = { dobText = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) },
                        label = { Text(uiText("Date of birth")) },
                        placeholder = { Text("YYYY-MM-DD") },
                        supportingText = {
                            Text(
                                uiText(when {
                                    dobInvalid -> "Use YYYY-MM-DD, not a future date."
                                    derivedAge != null -> "Age $derivedAge"
                                    else -> "Optional"
                                })
                            )
                        },
                        isError = dobInvalid,
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = weightText,
                        onValueChange = { raw -> weightText = raw.filter { it.isDigit() || it == '.' }.take(6) },
                        label = { Text(uiText("Weight (kg)")) },
                        supportingText = { Text(uiText(if (weightInvalid) "Enter 20-350 kg." else "Optional")) },
                        isError = weightInvalid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    AppActionRow {
                        PrimaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = "Save",
                            enabled = canSave,
                            onClick = {
                                onUserProfileSave(
                                    UserProfile(
                                        sex = sex,
                                        dateOfBirthIso = parsedDob?.toString(),
                                        weightKg = weightText.toDoubleOrNull()?.takeIf { it in 20.0..350.0 }
                                    )
                                )
                                editing = false
                            }
                        )
                        SecondaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = "Cancel",
                            onClick = {
                                sex = userProfile.sex
                                dobText = userProfile.dateOfBirthIso.orEmpty()
                                weightText = userProfile.weightKg?.let(::formatProfileWeight).orEmpty()
                                editing = false
                            }
                        )
                    }
                }
            } else {
                ProfileValueRow("Sex", userProfile.sex.label)
                ProfileValueRow("Date of birth", userProfile.dateOfBirthIso ?: "Not set")
                ProfileValueRow("Age", userProfile.age?.let { "$it" } ?: "Not set")
                ProfileValueRow("Weight", userProfile.weightKg?.let { "${formatProfileWeight(it)} kg" } ?: "Not set")
                PrimaryActionButton("Edit", onClick = { editing = true })
            }
            Text(
                uiText("DOB-derived age affects HR reference bands. Other values are saved for later."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            uiText(label),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            uiText(value),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun profileSexShortLabel(sex: ProfileSex): String =
    when (sex) {
        ProfileSex.NOT_SET -> "Not set"
        ProfileSex.FEMALE -> "Female"
        ProfileSex.MALE -> "Male"
        ProfileSex.OTHER -> "Other"
    }

private fun formatProfileWeight(weightKg: Double): String =
    if (weightKg % 1.0 == 0.0) weightKg.toInt().toString() else "%.1f".format(weightKg)

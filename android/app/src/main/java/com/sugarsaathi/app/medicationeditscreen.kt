package com.sugarsaathi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Add or edit one medicine.
 *
 * Only the NAME is required. Everything else is optional, because a patient who
 * knows only "the white tablet twice a day" should still be able to record it -
 * partial information is more useful than an empty list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditScreen(
    existing: Medication?,
    medicationViewModel: MedicationViewModel,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dose by remember { mutableStateOf(existing?.dose ?: "") }
    var frequency by remember {
        mutableStateOf(existing?.frequencyEnum ?: MedFrequency.ONCE_DAILY)
    }
    var timing by remember { mutableStateOf(existing?.timingEnum ?: MedTiming.ANY_TIME) }
    var timesOfDay by remember { mutableStateOf(existing?.timesOfDay ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var isInsulin by remember { mutableStateOf(existing?.isInsulin ?: false) }
    // Tracks whether the user has overridden the automatic insulin guess, so
    // typing more of the name does not keep flipping their choice back.
    var insulinTouched by remember { mutableStateOf(existing != null) }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = {
                Text(
                    stringResource(if (existing == null) R.string.med_new else R.string.med_edit),
                    fontSize = 18.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TealGreen,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            SectionLabel(stringResource(R.string.med_field_name))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (!insulinTouched) {
                        isInsulin = MedicationViewModel.looksLikeInsulin(it)
                    }
                },
                placeholder = { Text(stringResource(R.string.med_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.med_field_dose))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.med_dose_help),
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = dose,
                onValueChange = { dose = it },
                placeholder = { Text(stringResource(R.string.med_dose_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.med_field_frequency))
            Spacer(Modifier.height(8.dp))
            MedFrequency.entries.forEach { option ->
                SelectableCard(
                    title = stringResource(option.labelRes),
                    subtitle = "",
                    isSelected = frequency == option,
                    onClick = { frequency = option }
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(14.dp))

            SectionLabel(stringResource(R.string.med_field_timing))
            Spacer(Modifier.height(8.dp))
            MedTiming.entries.forEach { option ->
                SelectableCard(
                    title = stringResource(option.labelRes),
                    subtitle = "",
                    isSelected = timing == option,
                    onClick = { timing = option }
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(14.dp))

            SectionLabel(stringResource(R.string.med_field_times))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = timesOfDay,
                onValueChange = { timesOfDay = it },
                placeholder = { Text(stringResource(R.string.med_times_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(Modifier.height(20.dp))

            // Insulin flag. Matters because the assistant treats insulin more
            // cautiously than oral medicines.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isInsulin) Color(0xFFE1F5EE)
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    if (isInsulin) 2.dp else 0.5.dp,
                    if (isInsulin) TealGreen else Color.LightGray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💉", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.med_is_insulin),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.med_is_insulin_help),
                            fontSize = 11.5.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = isInsulin,
                        onCheckedChange = {
                            isInsulin = it
                            insulinTouched = true
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TealGreen
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.med_field_notes))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text(stringResource(R.string.med_notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = {
                    medicationViewModel.save(
                        Medication(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            dose = dose.trim(),
                            frequency = frequency.name,
                            timing = timing.name,
                            timesOfDay = timesOfDay.trim(),
                            isInsulin = isInsulin,
                            notes = notes.trim(),
                            active = existing?.active ?: true,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                shape = RoundedCornerShape(14.dp),
                // Name is the only requirement - partial detail beats none.
                enabled = name.isNotBlank()
            ) {
                Text(
                    stringResource(R.string.med_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D5A44))
}
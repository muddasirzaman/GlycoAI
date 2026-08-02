package com.sugarsaathi.app

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReadingScreen(
    defaultUnit: String,
    glucoseViewModel: GlucoseViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var value by remember { mutableStateOf("") }
    var readingType by remember { mutableStateOf("Fasting") }
    var note by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val errValidNumber = stringResource(R.string.enter_valid_number)
    val errRangeMgdl = stringResource(R.string.range_error_mgdl)
    val errRangeMmol = stringResource(R.string.range_error_mmol)

    val readingTypes = listOf(
        "Fasting" to stringResource(R.string.rt_fasting),
        "Before Breakfast" to stringResource(R.string.rt_before_breakfast),
        "After Breakfast" to stringResource(R.string.rt_after_breakfast),
        "Before Lunch" to stringResource(R.string.rt_before_lunch),
        "After Lunch" to stringResource(R.string.rt_after_lunch),
        "Before Dinner" to stringResource(R.string.rt_before_dinner),
        "After Dinner" to stringResource(R.string.rt_after_dinner),
        "Bedtime" to stringResource(R.string.rt_bedtime),
        "Random" to stringResource(R.string.rt_random)
    )

    val quickNotes = listOf(
        "Feeling dizzy" to stringResource(R.string.qn_dizzy),
        "Ate sweets" to stringResource(R.string.qn_sweets),
        "Exercised" to stringResource(R.string.qn_exercised),
        "Missed medication" to stringResource(R.string.qn_missed_med)
    )

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.add_glucose_reading), fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Glucose value
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.blood_glucose_reading), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(" *", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it.filter { c -> c.isDigit() || c == '.' }
                    errorText = null
                },
                placeholder = { Text(stringResource(R.string.glucose_hint)) },
                suffix = { Text(defaultUnit) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(Modifier.height(20.dp))

            // Reading type
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.when_taken), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(" *", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            readingTypes.forEach { (value, label) ->
                ReadingTypeRow(
                    label = label,
                    selected = readingType == value,
                    onClick = { readingType = value }
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(20.dp))

            // Quick notes
            Text(stringResource(R.string.notes_optional), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            quickNotes.forEach { (value, label) ->
                ReadingTypeRow(
                    label = label,
                    selected = note == value,
                    onClick = { note = if (note == value) "" else value }
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = if (quickNotes.any { it.first == note }) "" else note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.type_own_note)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            errorText?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    val num = value.toFloatOrNull()
                    if (num == null) {
                        errorText = errValidNumber
                        return@Button
                    }
                    val ok = glucoseViewModel.addReading(
                        value = num,
                        unit = defaultUnit,
                        readingType = readingType,
                        note = note
                    )
                    if (ok) {
                        onSaved()
                    } else {
                        errorText = if (defaultUnit == "mmol/L") errRangeMmol else errRangeMgdl
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen)
            ) {
                Text(stringResource(R.string.save_reading), fontSize = 16.sp)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun ReadingTypeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE1F5EE)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 0.5.dp,
            color = if (selected) TealGreen else Color.LightGray
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
            if (selected) {
                Text("✓", color = TealGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}


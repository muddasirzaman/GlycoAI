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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Edits ONE section of the profile at a time.
 *
 * Reuses the exact widgets onboarding uses (SelectableCard, SelectableButton,
 * OutlinedTextField) so the two never look like different apps. Saving builds
 * the new profile with profile.copy(...) - only this section's fields change,
 * everything else the patient entered is carried through untouched.
 *
 * Every field here is optional. There is no required-field gate: a patient
 * correcting one phone number must not be blocked because some unrelated field
 * is blank. Onboarding still enforces the essentials at first run; this screen
 * is for maintenance, where partial is fine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSectionEditScreen(
    section: ProfileSection,
    profile: UserProfileData,
    onDone: () -> Unit,
    onSaved: (UserProfileData) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ProfileRepository(context) }

    // ---- Local editable state, seeded from the current profile ----
    var name by remember { mutableStateOf(profile.name) }
    var age by remember { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var sex by remember { mutableStateOf(profile.sex) }
    var height by remember { mutableStateOf(profile.heightCm?.let { trimNum(it) } ?: "") }
    var weight by remember { mutableStateOf(profile.weightKg?.let { trimNum(it) } ?: "") }

    // feet/inches are just a friendlier UI on top of the same "height" cm
    // string above — buildUpdated() below still reads heightCm from `height`
    // unchanged, so nothing else in this file needs to know feet/inches exist.
    var feetText by remember {
        val cm = profile.heightCm
        mutableStateOf(if (cm != null && cm > 0) cmToFeetInches(cm).first.toString() else "")
    }
    var inchesText by remember {
        val cm = profile.heightCm
        mutableStateOf(if (cm != null && cm > 0) cmToFeetInches(cm).second.toString() else "")
    }
    fun pushHeight(f: String, i: String) {
        val feet = f.toIntOrNull()
        val inches = i.toIntOrNull()
        height = if (feet == null && inches == null) ""
        else feetInchesToCm(feet ?: 0, inches ?: 0).toString()
    }
    var diabetesType by remember { mutableStateOf(profile.diabetesType) }
    var diagnosisYear by remember { mutableStateOf(profile.diagnosisYear ?: "") }
    var glucoseUnit by remember { mutableStateOf(profile.glucoseUnit.ifBlank { "mg/dL" }) }
    var hba1c by remember { mutableStateOf(profile.hba1c?.let { trimNum(it) } ?: "") }
    var hba1cDate by remember { mutableStateOf(profile.hba1cDate ?: "") }

    var insulinType by remember { mutableStateOf(profile.insulinType ?: "") }

    var allergies by remember {
        mutableStateOf(profile.allergies.joinToString(", "))
    }
    // Conditions are shown/edited as one comma list. On save they are written
    // back to BOTH complications and otherConditions, matching onboarding, so
    // the backend's kidney / heart / BP rules keep firing.
    var conditions by remember {
        mutableStateOf(
            (profile.complications + profile.otherConditions)
                .distinct().filter { it.isNotBlank() }.joinToString(", ")
        )
    }

    var doctorName by remember { mutableStateOf(profile.doctorName) }
    var doctorPhone by remember { mutableStateOf(profile.doctorPhone) }
    var contactName by remember { mutableStateOf(profile.emergencyContactName) }
    var contactPhone by remember { mutableStateOf(profile.emergencyContactPhone) }

    fun commaList(s: String) =
        s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // Builds the updated profile for the CURRENT section only. copy() leaves
    // every other field exactly as it was.
    fun buildUpdated(): UserProfileData = when (section) {
        ProfileSection.PERSONAL -> profile.copy(
            name = name.trim(),
            age = age.toIntOrNull() ?: profile.age,
            sex = sex,
            heightCm = height.toFloatOrNull(),
            weightKg = weight.toFloatOrNull()
        )
        ProfileSection.DIABETES -> profile.copy(
            diabetesType = diabetesType.ifBlank { "unknown" },
            diagnosisYear = diagnosisYear.ifBlank { null },
            glucoseUnit = glucoseUnit,
            hba1c = hba1c.toFloatOrNull(),
            hba1cDate = hba1cDate.ifBlank { null }
        )
        ProfileSection.TREATMENT -> profile.copy(
            insulinType = insulinType.ifBlank { null }
        )
        ProfileSection.MEDICAL -> profile.copy(
            allergies = commaList(allergies),
            complications = commaList(conditions),
            otherConditions = commaList(conditions)
        )
        ProfileSection.PROVIDER -> profile.copy(
            doctorName = doctorName.trim(),
            doctorPhone = doctorPhone.trim()
        )
        ProfileSection.EMERGENCY -> profile.copy(
            emergencyContactName = contactName.trim(),
            emergencyContactPhone = contactPhone.trim()
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(sectionTitle(section), fontSize = 18.sp) },
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
            when (section) {

                ProfileSection.PERSONAL -> {
                    LabeledField(stringResource(R.string.field_name), name, { name = it })
                    LabeledField(
                        stringResource(R.string.field_age), age, { age = it.filter(Char::isDigit) }
                    )
                    FieldLabel(stringResource(R.string.field_sex))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectableButton(stringResource(R.string.male), sex == "Male") { sex = "Male" }
                        SelectableButton(stringResource(R.string.female), sex == "Female") { sex = "Female" }
                        SelectableButton(stringResource(R.string.other), sex == "Other") { sex = "Other" }
                    }
                    Spacer(Modifier.height(16.dp))
                    FieldLabel(stringResource(R.string.field_height))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = feetText,
                            onValueChange = { new ->
                                val filtered = new.filter { it.isDigit() }.take(1)
                                feetText = filtered
                                pushHeight(filtered, inchesText)
                            },
                            label = { Text(stringResource(R.string.height_feet)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black, unfocusedTextColor = Color.Black
                            )
                        )
                        OutlinedTextField(
                            value = inchesText,
                            onValueChange = { new ->
                                val filtered = new.filter { it.isDigit() }.take(2)
                                inchesText = filtered
                                pushHeight(feetText, filtered)
                            },
                            label = { Text(stringResource(R.string.height_inches)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black, unfocusedTextColor = Color.Black
                            )
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LabeledField(
                        stringResource(R.string.field_weight), weight,
                        { weight = it.filter { c -> c.isDigit() || c == '.' } }
                    )

                    Spacer(Modifier.height(16.dp))
                    LabeledField(
                        stringResource(R.string.field_height), height,
                        { height = it.filter { c -> c.isDigit() || c == '.' } }
                    )
                    LabeledField(
                        stringResource(R.string.field_weight), weight,
                        { weight = it.filter { c -> c.isDigit() || c == '.' } }
                    )
                }

                ProfileSection.DIABETES -> {
                    FieldLabel(stringResource(R.string.field_diabetes_type))
                    val types = listOf(
                        "type1" to stringResource(R.string.type1),
                        "type2" to stringResource(R.string.type2),
                        "prediabetes" to stringResource(R.string.prediabetes),
                        "gestational" to "Gestational",
                        "unknown" to stringResource(R.string.not_sure)
                    )
                    types.forEach { (value, label) ->
                        SelectableCard(
                            title = label,
                            subtitle = "",
                            isSelected = diabetesType == value,
                            onClick = { diabetesType = value }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    LabeledField(
                        stringResource(R.string.field_diagnosis_year), diagnosisYear,
                        { diagnosisYear = it.filter(Char::isDigit) }
                    )
                    FieldLabel(stringResource(R.string.field_glucose_unit))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectableButton("mg/dL", glucoseUnit == "mg/dL") { glucoseUnit = "mg/dL" }
                        SelectableButton("mmol/L", glucoseUnit == "mmol/L") { glucoseUnit = "mmol/L" }
                    }
                    Spacer(Modifier.height(16.dp))
                    LabeledField(
                        stringResource(R.string.field_hba1c), hba1c,
                        { hba1c = it.filter { c -> c.isDigit() || c == '.' } }
                    )
                    LabeledField(
                        stringResource(R.string.field_hba1c_date), hba1cDate, { hba1cDate = it },
                        hint = stringResource(R.string.hba1c_date_hint)
                    )
                }

                ProfileSection.TREATMENT -> {
                    Text(
                        stringResource(R.string.treatment_edit_note),
                        fontSize = 13.sp,
                        color = Color.Gray,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    FieldLabel(stringResource(R.string.field_insulin_type))
                    val insulinOptions = listOf(
                        "Rapid-acting" to "Rapid-acting (NovoRapid, Humalog)",
                        "Short-acting" to "Short-acting / Regular (Actrapid)",
                        "Intermediate" to "Intermediate (NPH, Insulatard)",
                        "Long-acting" to "Long-acting (Lantus, Levemir)",
                        "Premixed" to "Premixed (Mixtard, Novomix)",
                        "Multiple" to "More than one type",
                        "Unknown" to "I don't know"
                    )
                    insulinOptions.forEach { (value, label) ->
                        SelectableCard(
                            title = label,
                            subtitle = "",
                            isSelected = insulinType == value,
                            onClick = { insulinType = if (insulinType == value) "" else value }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                ProfileSection.MEDICAL -> {
                    LabeledField(
                        stringResource(R.string.field_allergies), allergies, { allergies = it },
                        hint = stringResource(R.string.separate_commas_generic),
                        minLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledField(
                        stringResource(R.string.field_conditions), conditions, { conditions = it },
                        hint = stringResource(R.string.separate_commas_generic),
                        minLines = 2
                    )
                }

                ProfileSection.PROVIDER -> {
                    LabeledField(stringResource(R.string.field_doctor_name), doctorName, { doctorName = it })
                    LabeledField(
                        stringResource(R.string.field_doctor_phone), doctorPhone,
                        { doctorPhone = it }, hint = stringResource(R.string.phone_hint)
                    )
                }

                ProfileSection.EMERGENCY -> {
                    LabeledField(stringResource(R.string.field_contact_name), contactName, { contactName = it })
                    LabeledField(
                        stringResource(R.string.field_contact_phone), contactPhone,
                        { contactPhone = it }, hint = stringResource(R.string.phone_hint)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = {
                    val updated = buildUpdated()
                    scope.launch {
                        repo.saveProfile(updated)
                        // Hand the fresh copy back so the caller's in-memory
                        // profile updates without waiting for a flow round-trip.
                        onSaved(updated)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    stringResource(R.string.save_changes),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF0D5A44),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

/** Label + outlined text field, with an optional grey hint under the label. */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String? = null,
    minLines: Int = 1
) {
    FieldLabel(label)
    if (hint != null) {
        Text(hint, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = minLines == 1,
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black
        )
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun sectionTitle(section: ProfileSection): String = when (section) {
    ProfileSection.PERSONAL -> stringResource(R.string.section_personal)
    ProfileSection.DIABETES -> stringResource(R.string.section_diabetes)
    ProfileSection.TREATMENT -> stringResource(R.string.section_treatment)
    ProfileSection.MEDICAL -> stringResource(R.string.section_medical)
    ProfileSection.PROVIDER -> stringResource(R.string.section_provider)
    ProfileSection.EMERGENCY -> stringResource(R.string.section_emergency)
}

private fun trimNum(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else v.toString()
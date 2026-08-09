package com.sugarsaathi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
import android.content.res.Configuration
import java.util.Locale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// Brands in the medication list that are insulins. Selecting any of these
// means the insulin-type question is relevant even for type 2.
private val INSULIN_BRANDS = setOf("Mixtard", "Lantus")

// Total number of *counted* onboarding steps - screens 1 (Language) and
// 2 (Consent) are intentionally not part of this count, since the progress
// indicator only appears from screen 3 onward. Counted screens are
// 3, 4, 5, 6, 7, 8 - that's 6 total. Single source of truth for the
// progress bar and the "Step X of Y" text.
private const val TOTAL_ONBOARDING_STEPS = 6

@Composable
fun OnboardingScreen(onComplete: (UserProfileData) -> Unit) {

    var currentScreen by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("") }
    var diagnosisYear by remember { mutableStateOf("") }
    var hba1c by remember { mutableStateOf("") }
    var glucoseUnit by remember { mutableStateOf("mg/dL") }
    var diabetesType by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("en") }
    var selectedMeds by remember { mutableStateOf(setOf<String>()) }
    var otherMedicine by remember { mutableStateOf("") }
    var complicationText by remember { mutableStateOf("") }
    var selectedConditions by remember { mutableStateOf(setOf<String>()) }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var smoking by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf("") }
    var treatment by remember { mutableStateOf(setOf<String>()) }
    var monitoring by remember { mutableStateOf("") }
    var emergencies by remember { mutableStateOf(setOf<String>()) }
    var dietPlan by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var hba1cDate by remember { mutableStateOf("") }
    var doctorName by remember { mutableStateOf("") }
    var doctorPhone by remember { mutableStateOf("") }
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }
    // NEW: optional, and only asked when insulin is actually relevant.
    var insulinType by remember { mutableStateOf("") }

    // Build a context whose locale follows the chosen language — live, no restart
    val layoutDirection = if (language == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr
    val baseContext = LocalContext.current
    // Read the configuration from LocalConfiguration, not from
    // context.resources - the composition local is recomposition-aware.
    val baseConfiguration = LocalConfiguration.current
    val localizedContext = remember(language, baseConfiguration) {
        // Locale(String) is deprecated; forLanguageTag is the replacement.
        val locale = Locale.forLanguageTag(language)
        val config = Configuration(baseConfiguration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        baseContext.createConfigurationContext(config)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalLayoutDirection provides layoutDirection
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Onboarding renders outside the Scaffold, so with
                // enableEdgeToEdge() it gets no automatic insets. Without this
                // the progress text slides under the clock and the bottom
                // content under the gesture bar.
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Steps 1 (Language) and 2 (Consent) aren't part of the counted
            // questionnaire - the visible "Step X of 6" only starts once the
            // user reaches Purpose (screen 3). Everything before that has no
            // progress indicator at all.
            if (currentScreen >= 3) {
                val displayedStep = currentScreen - 2   // 3→1, 4→2, ..., 8→6

                Text(
                    text = stringResource(
                        R.string.step_progress,
                        displayedStep,
                        TOTAL_ONBOARDING_STEPS
                    ),
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LinearProgressIndicator(
                    progress = { displayedStep / TOTAL_ONBOARDING_STEPS.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    color = TealGreen
                )
            }

            when (currentScreen) {

                1 -> Screen1Language(
                    selectedLanguage = language,
                    onLanguageSelected = { language = it },
                    onNext = { currentScreen = 2 }
                )

                // Consent sits after language so it is read in the user's own
                // language, and before any medical question so nothing personal
                // is collected before they know what this app is.
                2 -> ConsentScreen(
                    onAccept = { currentScreen = 3 },
                    onBack = { currentScreen = 1 }
                )

                3 -> PurposeScreen(
                    onPurposeSelected = { chosen ->
                        purpose = chosen
                        if (chosen == "patient") {
                            currentScreen = 4   // continue to medical onboarding
                        } else {
                            // non-patient: complete immediately, go to chat
                            onComplete(
                                UserProfileData(
                                    name = "Friend",
                                    age = 30,
                                    diabetesType = "none",
                                    language = language,
                                    purpose = chosen,
                                    consentAccepted = true,
                                    consentTimestamp = System.currentTimeMillis(),
                                    onboardingDone = true
                                )
                            )
                        }
                    }
                )

                4 -> Screen2BasicInfo(
                    name = name,
                    age = age,
                    sex = sex,
                    diagnosisYear = diagnosisYear,
                    hba1c = hba1c,
                    glucoseUnit = glucoseUnit,
                    onNameChange = { name = it },
                    onAgeChange = { age = it },
                    onSexChange = { sex = it },
                    onDiagnosisYearChange = { diagnosisYear = it },
                    onHba1cChange = { hba1c = it },
                    onGlucoseUnitChange = { glucoseUnit = it },
                    onNext = { currentScreen = 5 },
                    onBack = { currentScreen = 3 }
                )

                5 -> Screen3DiabetesType(
                    selectedType = diabetesType,
                    onTypeSelected = { diabetesType = it },
                    onNext = { currentScreen = 6 },
                    onBack = { currentScreen = 4 }
                )

                6 -> Screen4Conditions(
                    selectedConditions = selectedConditions,
                    otherCondition = complicationText,
                    onOtherConditionChange = { complicationText = it },

                    onConditionToggle = { condition ->
                        val wasSelected = condition in selectedConditions

                        selectedConditions =
                            if (condition == "None") {
                                if (wasSelected) {
                                    emptySet()
                                } else {
                                    setOf("None")
                                }
                            } else {
                                val withoutNone = selectedConditions - "None"

                                if (condition in withoutNone) {
                                    withoutNone - condition
                                } else {
                                    withoutNone + condition
                                }
                            }

                        if (condition == "Other" && wasSelected) {
                            complicationText = ""
                        }
                    },

                    onBack = { currentScreen = 5 },
                    onNext = { currentScreen = 7 }
                )

                7 -> HealthProfileScreen(
                    weight = weight,
                    height = height,
                    smoking = smoking,
                    activity = activity,
                    treatment = treatment,
                    monitoring = monitoring,
                    emergencies = emergencies,
                    dietPlan = dietPlan,
                    onWeightChange = { weight = it },
                    onHeightChange = { height = it },
                    onSmokingChange = { smoking = it },
                    onActivityChange = { activity = it },
                    onTreatmentToggle = { item ->
                        treatment = if (treatment.contains(item)) treatment - item else treatment + item
                    },
                    onMonitoringChange = { monitoring = it },
                    onEmergencyToggle = { item ->
                        emergencies = if (emergencies.contains(item)) emergencies - item else emergencies + item
                    },
                    onDietChange = { dietPlan = it },
                    onBack = { currentScreen = 6 },
                    onNext = { currentScreen = 8 },

                    // NEW
                    allergies = allergies,
                    onAllergiesChange = { allergies = it },
                    hba1cDate = hba1cDate,
                    onHba1cDateChange = { hba1cDate = it },
                    doctorName = doctorName,
                    onDoctorNameChange = { doctorName = it },
                    doctorPhone = doctorPhone,
                    onDoctorPhoneChange = { doctorPhone = it },
                    emergencyContactName = emergencyContactName,
                    onEmergencyContactNameChange = { emergencyContactName = it },
                    emergencyContactPhone = emergencyContactPhone,
                    onEmergencyContactPhoneChange = { emergencyContactPhone = it }
                )

                8 -> Screen5Medications(
                    selectedMeds = selectedMeds,
                    otherMedicine = otherMedicine,
                    diabetesType = diabetesType,
                    insulinType = insulinType,
                    onInsulinTypeChange = { insulinType = it },
                    onOtherMedicineChange = { otherMedicine = it },

                    onMedToggle = { med ->
                        val wasSelected = med in selectedMeds

                        selectedMeds =
                            if (med == "None") {
                                if (wasSelected) {
                                    emptySet()
                                } else {
                                    setOf("None")
                                }
                            } else {
                                val withoutNone = selectedMeds - "None"

                                if (med in withoutNone) {
                                    withoutNone - med
                                } else {
                                    withoutNone + med
                                }
                            }

                        if (med == "Other" && wasSelected) {
                            otherMedicine = ""
                        }

                        // If insulin is deselected entirely and they are not
                        // type 1, the answer no longer applies - clear it so a
                        // stale value never reaches the assistant.
                        val stillOnInsulin = (selectedMeds - "None").any { it in INSULIN_BRANDS }
                        if (!stillOnInsulin && diabetesType != "type1") {
                            insulinType = ""
                        }
                    },

                    onBack = { currentScreen = 7 },

                    onFinish = {
                        onComplete(
                            UserProfileData(
                                name = name.ifEmpty { "Friend" },
                                age = age.toIntOrNull() ?: 30,
                                sex = sex,
                                diagnosisYear = diagnosisYear.ifEmpty { null },
                                hba1c = hba1c.toFloatOrNull(),
                                glucoseUnit = glucoseUnit,
                                diabetesType = diabetesType.ifEmpty { "unknown" },
                                language = language,
                                weightKg = weight.toFloatOrNull(),
                                heightCm = height.toFloatOrNull(),
                                smokingStatus = smoking,
                                activityLevel = activity,
                                treatmentApproach = treatment.toList(),
                                monitoringMethod = monitoring,
                                emergencyHistory = emergencies.toList(),
                                dietPlan = dietPlan,
                                allergies = allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                hba1cDate = hba1cDate.ifBlank { null },
                                doctorName = doctorName,
                                doctorPhone = doctorPhone,
                                emergencyContactName = emergencyContactName,
                                emergencyContactPhone = emergencyContactPhone,
                                // NEW: optional - null when not asked or not answered
                                insulinType = insulinType.ifBlank { null },

                                medications = selectedMeds
                                    .filter { it != "Other" && it != "None" }
                                    .plus(
                                        if ("Other" in selectedMeds) {
                                            otherMedicine.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        } else emptyList()
                                    ),
                                complications = selectedConditions
                                    .filter { it != "Other" && it != "None" }
                                    .plus(
                                        if ("Other" in selectedConditions) {
                                            complicationText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        } else emptyList()
                                    ),

                                // Send the same list as otherConditions too, so the
                                // backend's kidney / heart / BP rules see them.
                                otherConditions = selectedConditions
                                    .filter { it != "Other" && it != "None" }
                                    .plus(
                                        if ("Other" in selectedConditions) {
                                            complicationText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        } else emptyList()
                                    ),

                                consentAccepted = true,
                                consentTimestamp = System.currentTimeMillis(),
                                onboardingDone = true
                            )
                        )
                    }
                )
            }
        }
    }
}


// ─── Screen 1: Language ───────────────────────────────

@Composable
fun Screen1Language(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Text("👋", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.daily_companion),
            fontSize = 18.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.select_language),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(16.dp))

        SelectableButton(
            text = stringResource(R.string.english),
            isSelected = selectedLanguage == "en",
            onClick = { onLanguageSelected("en") }
        )
        Spacer(modifier = Modifier.height(12.dp))
        SelectableButton(
            text = "اردو",
            isSelected = selectedLanguage == "ur",
            onClick = { onLanguageSelected("ur") }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TealGreen)
        ) {
            Text(stringResource(R.string.next_button), fontSize = 16.sp)
        }

        // Push institutional branding to the bottom so it never competes with
        // the app name or the primary action above it.
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))
        OrganizationLogos()
    }
}

// ─── Screen 2: Basic Info ─────────────────────────────
@Composable
fun Screen2BasicInfo(
    name: String,
    age: String,
    sex: String,
    diagnosisYear: String,
    hba1c: String,
    glucoseUnit: String,
    onNameChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onSexChange: (String) -> Unit,
    onDiagnosisYearChange: (String) -> Unit,
    onHba1cChange: (String) -> Unit,
    onGlucoseUnitChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("👤", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.tell_us_about_yourself),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { RequiredLabel(stringResource(R.string.your_name)) },
            placeholder = { Text(stringResource(R.string.name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = age,
            onValueChange = onAgeChange,
            label = { RequiredLabel(stringResource(R.string.your_age)) },
            placeholder = { Text(stringResource(R.string.age_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.gender),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(" *", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableButton(stringResource(R.string.male), sex == "Male") { onSexChange("Male") }
            SelectableButton(stringResource(R.string.female), sex == "Female") { onSexChange("Female") }
            SelectableButton(stringResource(R.string.other), sex == "Other") { onSexChange("Other") }
        }
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = diagnosisYear,
            onValueChange = onDiagnosisYearChange,
            label = { Text(stringResource(R.string.year_diagnosed)) },
            placeholder = { Text(stringResource(R.string.year_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.blood_sugar_unit),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableButton("mg/dL", glucoseUnit == "mg/dL") {
                onGlucoseUnitChange("mg/dL")
            }
            SelectableButton("mmol/L", glucoseUnit == "mmol/L") {
                onGlucoseUnitChange("mmol/L")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = hba1c,
            onValueChange = onHba1cChange,
            label = { Text(stringResource(R.string.last_hba1c)) },
            placeholder = { Text(stringResource(R.string.hba1c_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dont_know_hba1c),
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.back_button)) }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                enabled = name.isNotEmpty() && age.isNotEmpty() && sex.isNotEmpty()
            ) { Text(stringResource(R.string.next_button)) }
        }
    }
}

// ─── Screen 3: Diabetes Type ──────────────────────────

@Composable
fun Screen3DiabetesType(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val types = listOf(
        Triple("type1", stringResource(R.string.type1), stringResource(R.string.type1_desc)),
        Triple("type2", stringResource(R.string.type2), stringResource(R.string.type2_desc)),
        Triple("prediabetes", stringResource(R.string.prediabetes), stringResource(R.string.prediabetes_desc)),
        Triple("unknown", stringResource(R.string.not_sure), stringResource(R.string.not_sure_desc)),
        Triple("none", stringResource(R.string.none_option), stringResource(R.string.none_desc))
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("🩺", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.diabetes_type_question),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(" *", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        types.forEach { (value, title, subtitle) ->
            SelectableCard(
                title = title,
                subtitle = subtitle,
                isSelected = selectedType == value,
                onClick = { onTypeSelected(value) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.back_button))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                enabled = selectedType.isNotEmpty()
            ) {
                Text(stringResource(R.string.next_button))
            }
        }
    }
}

// ─── Screen 4: conditions ────────────────────────────
@Composable
fun Screen4Conditions(
    selectedConditions: Set<String>,
    otherCondition: String,
    onOtherConditionChange: (String) -> Unit,
    onConditionToggle: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val conditions = listOf(
        "Kidney disease" to stringResource(R.string.cond_kidney),
        "Heart disease" to stringResource(R.string.cond_heart),
        "High blood pressure" to stringResource(R.string.cond_bp),
        "Eye problems / retinopathy" to stringResource(R.string.cond_eye),
        "Nerve problems / neuropathy" to stringResource(R.string.cond_nerve),
        "Foot ulcer or previous amputation" to stringResource(R.string.cond_foot),
        "Pregnant or planning pregnancy" to stringResource(R.string.cond_pregnant),
        "None" to stringResource(R.string.cond_none),
        "Other" to stringResource(R.string.cond_other)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("❤️", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.related_conditions),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.select_all_apply),
            fontSize = 14.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(20.dp))

        conditions.forEach { (value, label) ->
            SelectableCard(
                title = label,
                subtitle = "",
                isSelected = selectedConditions.contains(value),
                onClick = { onConditionToggle(value) }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if ("Other" in selectedConditions) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = otherCondition,
                onValueChange = onOtherConditionChange,
                label = { Text(stringResource(R.string.enter_other_condition)) },
                placeholder = { Text(stringResource(R.string.separate_commas_condition)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.back_button))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                enabled = selectedConditions.isNotEmpty() &&
                        ("Other" !in selectedConditions || otherCondition.isNotBlank())
            ) {
                Text(stringResource(R.string.next_button))
            }
        }
    }
}

// ─── Screen 5: Medications (+ conditional insulin type) ───────

@Composable
fun Screen5Medications(
    selectedMeds: Set<String>,
    otherMedicine: String,
    diabetesType: String,
    insulinType: String,
    onInsulinTypeChange: (String) -> Unit,
    onOtherMedicineChange: (String) -> Unit,
    onMedToggle: (String) -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val medications = listOf(
        "Glucophage" to "Glucophage",
        "Mixtard" to "Mixtard",
        "Amaryl" to "Amaryl",
        "Diamicron" to "Diamicron",
        "Lantus" to "Lantus",
        "None" to stringResource(R.string.med_none),
        "Other" to stringResource(R.string.med_other)
    )

    // Only ask about insulin type when insulin is actually in the picture.
    val onInsulin = selectedMeds.any { it in INSULIN_BRANDS }
    val showInsulinQuestion = diabetesType == "type1" || onInsulin

    val insulinOptions = listOf(
        "Rapid-acting" to "Rapid-acting (NovoRapid, Humalog)",
        "Short-acting" to "Short-acting / Regular (Actrapid)",
        "Intermediate" to "Intermediate (NPH, Insulatard)",
        "Long-acting" to "Long-acting (Lantus, Levemir)",
        "Premixed" to "Premixed (Mixtard, Novomix)",
        "Multiple" to "More than one type",
        "Unknown" to "I don't know"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("💊", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.current_medications),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(" *", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.select_all_apply),
            fontSize = 14.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(24.dp))

        medications.forEach { (value, label) ->
            SelectableCard(
                title = label,
                subtitle = "",
                isSelected = selectedMeds.contains(value),
                onClick = { onMedToggle(value) }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (selectedMeds.contains("Other")) {
            OutlinedTextField(
                value = otherMedicine,
                onValueChange = onOtherMedicineChange,
                label = { Text(stringResource(R.string.enter_medicine_names)) },
                placeholder = { Text(stringResource(R.string.separate_commas_medicine)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Conditional: insulin type ───────────────────────────
        if (showInsulinQuestion) {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Which insulin do you use?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Optional - it helps give safer advice about food and timing. " +
                        "Skip it if you are not sure.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            insulinOptions.forEach { (value, label) ->
                SelectableCard(
                    title = label,
                    subtitle = "",
                    isSelected = insulinType == value,
                    onClick = {
                        // Tapping the selected one again clears it - keeps the
                        // field genuinely optional.
                        onInsulinTypeChange(if (insulinType == value) "" else value)
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.back_button))
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                // Insulin type stays optional - it never blocks finishing.
                enabled = selectedMeds.isNotEmpty() &&
                        ("Other" !in selectedMeds || otherMedicine.isNotBlank())
            ) {
                Text(stringResource(R.string.start_chatting))
            }
        }
    }
}

// ─── Reusable Components ──────────────────────────────

@Composable
fun SelectableButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) Color(0xFFE1F5EE)
            else Color.Transparent
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) TealGreen else Color.Gray
        )
    ) {
        Text(
            text = text,
            color = if (isSelected) TealGreen else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold
            else FontWeight.Normal
        )
    }
}

@Composable
fun SelectableCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE1F5EE)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 0.5.dp,
            color = if (isSelected) TealGreen else Color.LightGray
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
            if (isSelected) {
                Text(
                    "✓",
                    color = TealGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RequiredLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text)
        Text(" *", color = Color.Red, fontWeight = FontWeight.Bold)
    }
}
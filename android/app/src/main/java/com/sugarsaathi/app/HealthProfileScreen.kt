package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HealthProfileScreen(
    weight: String,
    height: String,
    smoking: String,
    activity: String,
    treatment: Set<String>,
    monitoring: String,
    emergencies: Set<String>,
    dietPlan: String,
    onWeightChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onSmokingChange: (String) -> Unit,
    onActivityChange: (String) -> Unit,
    onTreatmentToggle: (String) -> Unit,
    onMonitoringChange: (String) -> Unit,
    onEmergencyToggle: (String) -> Unit,
    onDietChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    // BMI live calculation
    val bmiText: String? = remember(weight, height) {
        val w = weight.toFloatOrNull()
        val h = height.toFloatOrNull()
        if (w != null && h != null && h > 0) {
            val m = h / 100f
            val bmi = w / (m * m)
            "%.1f".format(bmi)
        } else null
    }
    val bmiValue = bmiText?.toFloatOrNull()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("🩺", fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.health_profile),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))

        // ── Physical ──
        SectionLabel(stringResource(R.string.physical_info))
        OutlinedTextField(
            value = height,
            onValueChange = onHeightChange,
            label = { Text(stringResource(R.string.height_cm)) },
            placeholder = { Text(stringResource(R.string.height_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black, unfocusedTextColor = Color.Black
            )
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = weight,
            onValueChange = onWeightChange,
            label = { Text(stringResource(R.string.weight_kg)) },
            placeholder = { Text(stringResource(R.string.weight_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black, unfocusedTextColor = Color.Black
            )
        )

        // BMI display
        if (bmiText != null && bmiValue != null) {
            Spacer(Modifier.height(12.dp))
            val category = when {
                bmiValue < 18.5f -> stringResource(R.string.bmi_underweight)
                bmiValue < 25f -> stringResource(R.string.bmi_normal)
                bmiValue < 30f -> stringResource(R.string.bmi_overweight)
                else -> stringResource(R.string.bmi_obese)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5EE))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${stringResource(R.string.bmi_label)}: $bmiText",
                        fontWeight = FontWeight.Bold, color = Color(0xFF0D5A44))
                    Text(category, color = Color(0xFF0D5A44))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Lifestyle ──
        SectionLabel(stringResource(R.string.lifestyle))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.smoking_status), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(" *", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableButton(stringResource(R.string.smoke_never), smoking == "Never") { onSmokingChange("Never") }
            SelectableButton(stringResource(R.string.smoke_former), smoking == "Former") { onSmokingChange("Former") }
            SelectableButton(stringResource(R.string.smoke_current), smoking == "Current") { onSmokingChange("Current") }
        }
        Spacer(Modifier.height(16.dp))


        FieldLabel(stringResource(R.string.activity_level))
        SelectableCard(stringResource(R.string.activity_sedentary), "", activity == "Sedentary") { onActivityChange("Sedentary") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.activity_light), "", activity == "Light") { onActivityChange("Light") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.activity_moderate), "", activity == "Moderate") { onActivityChange("Moderate") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.activity_very), "", activity == "Very") { onActivityChange("Very") }

        Spacer(Modifier.height(20.dp))

        // ── Treatment (multi-select) ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.treatment_approach),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TealGreen
            )
            Text(" *", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))

        MultiCard("Oral", stringResource(R.string.tr_oral), treatment, onTreatmentToggle)
        MultiCard("Insulin", stringResource(R.string.tr_insulin), treatment, onTreatmentToggle)
        MultiCard("Diet", stringResource(R.string.tr_diet), treatment, onTreatmentToggle)
        MultiCard("Exercise", stringResource(R.string.tr_exercise), treatment, onTreatmentToggle)
        MultiCard("None", stringResource(R.string.tr_none), treatment, onTreatmentToggle)

        Spacer(Modifier.height(20.dp))

        // ── Monitoring ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.monitoring_method), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TealGreen)
            Text(" *", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
        SelectableCard(stringResource(R.string.mon_glucometer), "", monitoring == "Glucometer") { onMonitoringChange("Glucometer") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.mon_cgm), "", monitoring == "CGM") { onMonitoringChange("CGM") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.mon_rarely), "", monitoring == "Rarely") { onMonitoringChange("Rarely") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.mon_never), "", monitoring == "Never") { onMonitoringChange("Never") }

        Spacer(Modifier.height(20.dp))

        // ── Emergency history (multi-select) ──
        SectionLabel(stringResource(R.string.emergency_history))
        MultiCard("SevereLow", stringResource(R.string.em_severe_low), emergencies, onEmergencyToggle)
        MultiCard("DKA", stringResource(R.string.em_dka), emergencies, onEmergencyToggle)
        MultiCard("Hospital", stringResource(R.string.em_hospital), emergencies, onEmergencyToggle)
        MultiCard("None", stringResource(R.string.em_none), emergencies, onEmergencyToggle)

        Spacer(Modifier.height(20.dp))

        // ── Diet plan ──
        SectionLabel(stringResource(R.string.diet_plan))
        SelectableCard(stringResource(R.string.diet_general), "", dietPlan == "General") { onDietChange("General") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.diet_lowcarb), "", dietPlan == "LowCarb") { onDietChange("LowCarb") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.diet_mediterranean), "", dietPlan == "Mediterranean") { onDietChange("Mediterranean") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.diet_vegetarian), "", dietPlan == "Vegetarian") { onDietChange("Vegetarian") }
        Spacer(Modifier.height(6.dp))
        SelectableCard(stringResource(R.string.diet_vegan), "", dietPlan == "Vegan") { onDietChange("Vegan") }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.back_button))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen)
            ) {
                Text(stringResource(R.string.next_button))
            }

            Button(
                onClick = onNext,
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                enabled = smoking.isNotBlank() && monitoring.isNotBlank() && treatment.isNotEmpty()
            ) {
                Text(stringResource(R.string.next_button))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = TealGreen,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )
}

@Composable
private fun MultiCard(value: String, label: String, selected: Set<String>, onToggle: (String) -> Unit) {
    SelectableCard(
        title = label,
        subtitle = "",
        isSelected = selected.contains(value),
        onClick = { onToggle(value) }
    )
    Spacer(Modifier.height(6.dp))
}
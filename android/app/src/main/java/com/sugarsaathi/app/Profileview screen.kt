package com.sugarsaathi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Which section of the profile is being edited. Drives ProfileSectionEditScreen
 * and mirrors the six headings the patient sees here. Kept as an enum rather
 * than an Int so a reordering of the list can never silently open the wrong
 * form.
 */
enum class ProfileSection {
    PERSONAL, DIABETES, TREATMENT, MEDICAL, PROVIDER, EMERGENCY
}

/**
 * Read-only, sectioned view of everything stored about the patient.
 *
 * This is the screen the "Edit my information" button now opens instead of
 * re-running onboarding. Onboarding stays as the first-run wizard; this is
 * where a patient lives afterwards - see and, section by section, change what
 * is stored.
 *
 * Every value is optional. An empty field shows "Not set" in grey rather than a
 * blank, so the patient can see at a glance what is still missing without it
 * looking broken.
 *
 * Nothing here writes to the backend or the database directly; edits go through
 * ProfileSectionEditScreen -> ProfileRepository, exactly as onboarding does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileViewScreen(
    profile: UserProfileData,
    onBack: () -> Unit,
    onEditSection: (ProfileSection) -> Unit,
    onOpenMedications: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.profile_view_title), fontSize = 18.sp) },
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
                .padding(16.dp)
        ) {

            // 🧑 Personal Information
            ProfileSectionCard(
                emoji = "🧑",
                title = stringResource(R.string.section_personal),
                onEdit = { onEditSection(ProfileSection.PERSONAL) }
            ) {
                InfoRow(stringResource(R.string.field_name), profile.name)
                InfoRow(
                    stringResource(R.string.field_age),
                    if (profile.age > 0) profile.age.toString() else null
                )
                InfoRow(stringResource(R.string.field_sex), profile.sex.ifBlank { null })
                InfoRow(
                    stringResource(R.string.field_height),
                    profile.heightCm?.let { "${trimNumber(it)} cm" }
                )
                InfoRow(
                    stringResource(R.string.field_weight),
                    profile.weightKg?.let { "${trimNumber(it)} kg" }
                )
            }

            Spacer(Modifier.height(12.dp))

            // 🩸 Diabetes Information
            ProfileSectionCard(
                emoji = "🩸",
                title = stringResource(R.string.section_diabetes),
                onEdit = { onEditSection(ProfileSection.DIABETES) }
            ) {
                InfoRow(
                    stringResource(R.string.field_diabetes_type),
                    diabetesTypeLabel(profile.diabetesType)
                )
                InfoRow(stringResource(R.string.field_diagnosis_year), profile.diagnosisYear)
                InfoRow(stringResource(R.string.field_glucose_unit), profile.glucoseUnit.ifBlank { null })
                InfoRow(
                    stringResource(R.string.field_hba1c),
                    profile.hba1c?.let { "${trimNumber(it)}%" }
                )
                InfoRow(stringResource(R.string.field_hba1c_date), profile.hba1cDate)
            }

            Spacer(Modifier.height(12.dp))

            // 💊 Treatment
            //
            // Deliberately does NOT edit medicines here. Structured medications
            // are owned by MedicationsScreen (the Stage 1 table). Letting this
            // section edit UserProfileData.medications too would create two
            // editors that disagree. So Treatment shows insulin type (which
            // still lives on the profile) and a read-only medicine count, with
            // a button that opens the one real editor.
            ProfileSectionCard(
                emoji = "💊",
                title = stringResource(R.string.section_treatment),
                onEdit = { onEditSection(ProfileSection.TREATMENT) }
            ) {
                InfoRow(stringResource(R.string.field_insulin_type), profile.insulinType)
                InfoRow(
                    stringResource(R.string.field_medicines),
                    if (profile.medications.isNotEmpty())
                        profile.medications.joinToString(", ")
                    else null
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenMedications,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealGreen),
                    border = BorderStroke(1.dp, TealGreen)
                ) { Text(stringResource(R.string.open_my_medicines)) }
            }

            Spacer(Modifier.height(12.dp))

            // 🏥 Medical Information
            ProfileSectionCard(
                emoji = "🏥",
                title = stringResource(R.string.section_medical),
                onEdit = { onEditSection(ProfileSection.MEDICAL) }
            ) {
                InfoRow(
                    stringResource(R.string.field_allergies),
                    if (profile.allergies.isNotEmpty()) profile.allergies.joinToString(", ") else null
                )
                InfoRow(
                    stringResource(R.string.field_conditions),
                    // Conditions live in both complications and otherConditions
                    // (onboarding dual-writes them). Reading the union here is
                    // safe even if one drifts.
                    (profile.complications + profile.otherConditions)
                        .distinct()
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                        .ifBlank { null }
                )
            }

            Spacer(Modifier.height(12.dp))

            // 🩺 Healthcare Provider
            ProfileSectionCard(
                emoji = "🩺",
                title = stringResource(R.string.section_provider),
                onEdit = { onEditSection(ProfileSection.PROVIDER) }
            ) {
                InfoRow(stringResource(R.string.field_doctor_name), profile.doctorName.ifBlank { null })
                InfoRow(stringResource(R.string.field_doctor_phone), profile.doctorPhone.ifBlank { null })
            }

            Spacer(Modifier.height(12.dp))

            // 🆘 Emergency Contact
            ProfileSectionCard(
                emoji = "🆘",
                title = stringResource(R.string.section_emergency),
                onEdit = { onEditSection(ProfileSection.EMERGENCY) }
            ) {
                InfoRow(
                    stringResource(R.string.field_contact_name),
                    profile.emergencyContactName.ifBlank { null }
                )
                InfoRow(
                    stringResource(R.string.field_contact_phone),
                    profile.emergencyContactPhone.ifBlank { null }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A titled card with an edit pencil and arbitrary rows inside. */
@Composable
private fun ProfileSectionCard(
    emoji: String,
    title: String,
    onEdit: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, Color.LightGray),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D5A44),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_button),
                        tint = TealGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.edit_button), color = TealGreen, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * One label/value line. A null or blank value renders as "Not set" in grey, so
 * missing information is visible rather than an empty gap.
 */
@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.weight(0.42f)
        )
        val shown = value?.takeIf { it.isNotBlank() }
        Text(
            shown ?: stringResource(R.string.value_not_set),
            fontSize = 14.sp,
            color = if (shown != null) Color.Black else Color.LightGray,
            fontWeight = if (shown != null) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(0.58f)
        )
    }
}

/** 70.0 -> "70", 70.5 -> "70.5". Keeps stored floats from showing a stray .0. */
private fun trimNumber(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else v.toString()

/** Maps the stored type code to a human label, falling back to the raw value. */
@Composable
private fun diabetesTypeLabel(type: String): String? = when (type.lowercase()) {
    "type1" -> stringResource(R.string.type1)
    "type2" -> stringResource(R.string.type2)
    "prediabetes" -> stringResource(R.string.prediabetes)
    "gestational" -> "Gestational"
    "none" -> stringResource(R.string.none_option)
    "unknown", "" -> null
    else -> type
}
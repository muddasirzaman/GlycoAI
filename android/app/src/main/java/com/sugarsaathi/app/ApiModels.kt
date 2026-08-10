package com.sugarsaathi.app


data class TipsRequest(
    val profile: ProfileData
)

data class TipsResponse(
    val tips: List<String>
)

@Suppress("PropertyName")
data class ProfileData(

    val name: String,
    val age: Int,
    val sex: String,
    val country: String,
    val diabetes_type: String,
    val diagnosis_year: String? = null,
    val insulin_type: String? = null,
    val medications: List<String> = emptyList(),
    val glucose_monitoring: String? = null,
    val severe_hypoglycemia: String? = null,
    val other_conditions: List<String> = emptyList(),
    val hba1c: Float? = null,
    val complications: List<String> = emptyList(),
    val language: String = "en",
    val response_style: String = "simple",
    val glucose_unit: String = "mg/dL",
    val known_facts: List<String> = emptyList(),
    val weight_kg: Float? = null,
    val height_cm: Float? = null,
    val smoking_status: String = "",
    val purpose: String = "patient",
    val glucose_summary: String? = null,

    // NEW: sent to the backend - these change what's safe to recommend.
    val allergies: List<String> = emptyList(),
    val hba1c_date: String? = null,

    // Deliberately NOT included: doctor_name, doctor_phone,
    // emergency_contact_name, emergency_contact_phone. Those are contact
    // details, not clinical context - a "can I eat mango" question has no
    // reason to carry a phone number to the backend.
)

fun UserProfileData.toApiProfileData(glucoseSummary: String? = null): ProfileData {
    return ProfileData(
        name = name,
        age = age,
        sex = sex,
        country = country,
        diabetes_type = diabetesType,
        diagnosis_year = diagnosisYear,
        insulin_type = insulinType,
        medications = medications,
        glucose_monitoring = glucoseMonitoring
            ?: monitoringMethod.ifBlank { null },
        severe_hypoglycemia = severeHypoglycemia
            ?: emergencyHistory.joinToString(", ").ifBlank { null },
        other_conditions = (otherConditions + complications).distinct(),
        hba1c = hba1c,
        complications = complications,
        language = language,
        response_style = responseStyle,
        glucose_unit = glucoseUnit,
        known_facts = knownFacts,
        weight_kg = weightKg,
        height_cm = heightCm,
        smoking_status = smokingStatus,
        purpose = purpose,
        glucose_summary = glucoseSummary,

        // NEW
        allergies = allergies,
        hba1c_date = hba1cDate,
    )
}

@Suppress("PropertyName")
data class ChatRequest(
    val message: String,
    val profile: ProfileData,
    val conversation_history: List<Map<String, String>>,
    val image_data: String? = null,
    val image_type: String? = null,
    val document_data: String? = null,
    val document_type: String? = null,
    val document_name: String? = null
)

@Suppress("PropertyName")
data class ChatResponse(
    val response: String,
    val safety_triggered: Boolean? = null,
    val needs_context: Boolean? = null,
    val quick_replies: List<String>? = null,
    val tier: String? = null
) {
    val needsContext: Boolean get() = needs_context == true
    val quickReplies: List<String> get() = quick_replies.orEmpty()
}

data class Message(
    val role: String,
    val content: String,
    // Only ever set on assistant messages, from the backend's ChatResponse.tier.
    // null for user messages and for older backend responses that omit it.
    val tier: String? = null
) {
    val isEmergency: Boolean get() = tier == "emergency"
}

@Suppress("PropertyName")
data class ExtractRequest(
    val conversation: List<Map<String, String>>,
    val existing_facts: List<String>
)

data class ExtractResponse(
    val facts: List<String>
)
package com.sugarsaathi.app

// What we send TO the server
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

    )

// Converts the app's onboarding profile into the shape the backend expects.
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
        glucose_monitoring = glucoseMonitoring,
        severe_hypoglycemia = severeHypoglycemia,
        other_conditions = otherConditions,
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
    )
}

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

// What we receive FROM the server
data class ChatResponse(
    val response: String,
    val safety_triggered: Boolean
)

// A single chat message (user or AI)
data class Message(
    val role: String,
    val content: String
)

data class ExtractRequest(
    val conversation: List<Map<String, String>>,
    val existing_facts: List<String>
)

data class ExtractResponse(
    val facts: List<String>
)

data class TipsRequest(val profile: ProfileData)
data class TipsResponse(val tips: List<String>)
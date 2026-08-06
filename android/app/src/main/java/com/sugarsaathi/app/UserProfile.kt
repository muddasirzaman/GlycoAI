package com.sugarsaathi.app

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Local storage file
val Context.dataStore by preferencesDataStore(name = "user_profile")

// Keys for storing each field
object ProfileKeys {
    val NAME = stringPreferencesKey("name")
    val AGE = intPreferencesKey("age")
    val SEX = stringPreferencesKey("sex")
    val DIAGNOSIS_YEAR = stringPreferencesKey("diagnosis_year")
    val HBA1C = stringPreferencesKey("hba1c")
    val GLUCOSE_UNIT = stringPreferencesKey("glucose_unit")
    val DIABETES_TYPE = stringPreferencesKey("diabetes_type")
    val LANGUAGE = stringPreferencesKey("language")
    val MEDICATIONS = stringPreferencesKey("medications")
    val COMPLICATIONS = stringPreferencesKey("complications")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val KNOWN_FACTS = stringPreferencesKey("known_facts")
    val WEIGHT = stringPreferencesKey("weight")
    val HEIGHT = stringPreferencesKey("height")
    val SMOKING = stringPreferencesKey("smoking")
    val COUNTRY = stringPreferencesKey("country")
    val OCCUPATION = stringPreferencesKey("occupation")
    val EDUCATION = stringPreferencesKey("education")
    val USED_CHATBOT = stringPreferencesKey("used_chatbot")
    val ACTIVITY = stringPreferencesKey("activity")
    val TREATMENT = stringPreferencesKey("treatment")
    val MONITORING = stringPreferencesKey("monitoring")
    val EMERGENCY_HISTORY = stringPreferencesKey("emergency_history")
    val DIET_PLAN = stringPreferencesKey("diet_plan")
    val PURPOSE = stringPreferencesKey("purpose")

    // FIX: these two existed on UserProfileData but were never persisted,
    // so insulin type was always null and other conditions always empty.
    val INSULIN_TYPE = stringPreferencesKey("insulin_type")
    val OTHER_CONDITIONS = stringPreferencesKey("other_conditions")
}


// Profile data class
data class UserProfileData(
    val name: String = "",
    val age: Int = 0,
    val sex: String = "",
    val country: String = "Pakistan",
    val diabetesType: String = "unknown",
    val diagnosisYear: String? = null,
    val insulinType: String? = null,
    val medications: List<String> = emptyList(),
    val glucoseMonitoring: String? = null,
    val severeHypoglycemia: String? = null,
    val otherConditions: List<String> = emptyList(),
    val hba1c: Float? = null,
    val complications: List<String> = emptyList(),
    val language: String = "en",
    val responseStyle: String = "simple",
    val glucoseUnit: String = "mg/dL",
    val onboardingDone: Boolean = false,
    val knownFacts: List<String> = emptyList(),
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    val smokingStatus: String = "",
    val occupation: String = "",
    val educationLevel: String = "",
    val usedChatbotBefore: String = "",
    val activityLevel: String = "",
    val treatmentApproach: List<String> = emptyList(),
    val monitoringMethod: String = "",
    val emergencyHistory: List<String> = emptyList(),
    val dietPlan: String = "",
    val purpose: String = "patient",
)

// Handles saving and loading profile
class ProfileRepository(private val context: Context) {

    // Read profile
    val profileFlow: Flow<UserProfileData> = context.dataStore.data.map { prefs ->
        UserProfileData(
            name = prefs[ProfileKeys.NAME] ?: "",
            age = prefs[ProfileKeys.AGE] ?: 0,
            sex = prefs[ProfileKeys.SEX] ?: "",
            diagnosisYear = prefs[ProfileKeys.DIAGNOSIS_YEAR]?.ifEmpty { null },
            hba1c = prefs[ProfileKeys.HBA1C]?.toFloatOrNull(),
            glucoseUnit = prefs[ProfileKeys.GLUCOSE_UNIT] ?: "mg/dL",
            diabetesType = prefs[ProfileKeys.DIABETES_TYPE] ?: "unknown",
            language = prefs[ProfileKeys.LANGUAGE] ?: "en",
            medications = (prefs[ProfileKeys.MEDICATIONS] ?: "")
                .split(",")
                .filter { it.isNotEmpty() },
            complications = (prefs[ProfileKeys.COMPLICATIONS] ?: "")
                .split(",")
                .filter { it.isNotEmpty() },
            onboardingDone = prefs[ProfileKeys.ONBOARDING_DONE] ?: false,
            knownFacts = (prefs[ProfileKeys.KNOWN_FACTS] ?: "")
                .split("|||")
                .filter { it.isNotEmpty() },
            weightKg = prefs[ProfileKeys.WEIGHT]?.toFloatOrNull(),
            heightCm = prefs[ProfileKeys.HEIGHT]?.toFloatOrNull(),
            smokingStatus = prefs[ProfileKeys.SMOKING] ?: "",
            country = prefs[ProfileKeys.COUNTRY] ?: "Pakistan",
            occupation = prefs[ProfileKeys.OCCUPATION] ?: "",
            educationLevel = prefs[ProfileKeys.EDUCATION] ?: "",
            usedChatbotBefore = prefs[ProfileKeys.USED_CHATBOT] ?: "",
            activityLevel = prefs[ProfileKeys.ACTIVITY] ?: "",
            treatmentApproach = (prefs[ProfileKeys.TREATMENT] ?: "")
                .split("|||").filter { it.isNotEmpty() },
            monitoringMethod = prefs[ProfileKeys.MONITORING] ?: "",
            emergencyHistory = (prefs[ProfileKeys.EMERGENCY_HISTORY] ?: "")
                .split("|||").filter { it.isNotEmpty() },
            dietPlan = prefs[ProfileKeys.DIET_PLAN] ?: "",
            purpose = prefs[ProfileKeys.PURPOSE] ?: "patient",

            // FIX: now actually read back
            insulinType = prefs[ProfileKeys.INSULIN_TYPE]?.ifEmpty { null },
            otherConditions = (prefs[ProfileKeys.OTHER_CONDITIONS] ?: "")
                .split("|||").filter { it.isNotEmpty() },
        )
    }

    // Save profile
    suspend fun saveProfile(profile: UserProfileData) {
        context.dataStore.edit { prefs ->
            prefs[ProfileKeys.NAME] = profile.name
            prefs[ProfileKeys.AGE] = profile.age
            prefs[ProfileKeys.SEX] = profile.sex
            prefs[ProfileKeys.DIAGNOSIS_YEAR] = profile.diagnosisYear ?: ""
            prefs[ProfileKeys.HBA1C] = profile.hba1c?.toString() ?: ""
            prefs[ProfileKeys.GLUCOSE_UNIT] = profile.glucoseUnit
            prefs[ProfileKeys.DIABETES_TYPE] = profile.diabetesType
            prefs[ProfileKeys.LANGUAGE] = profile.language
            prefs[ProfileKeys.MEDICATIONS] = profile.medications.joinToString(",")
            prefs[ProfileKeys.COMPLICATIONS] = profile.complications.joinToString(",")
            prefs[ProfileKeys.ONBOARDING_DONE] = true
            prefs[ProfileKeys.KNOWN_FACTS] = profile.knownFacts.joinToString("|||")
            prefs[ProfileKeys.WEIGHT] = profile.weightKg?.toString() ?: ""
            prefs[ProfileKeys.HEIGHT] = profile.heightCm?.toString() ?: ""
            prefs[ProfileKeys.SMOKING] = profile.smokingStatus
            prefs[ProfileKeys.COUNTRY] = profile.country
            prefs[ProfileKeys.OCCUPATION] = profile.occupation
            prefs[ProfileKeys.EDUCATION] = profile.educationLevel
            prefs[ProfileKeys.USED_CHATBOT] = profile.usedChatbotBefore
            prefs[ProfileKeys.ACTIVITY] = profile.activityLevel
            prefs[ProfileKeys.TREATMENT] = profile.treatmentApproach.joinToString("|||")
            prefs[ProfileKeys.MONITORING] = profile.monitoringMethod
            prefs[ProfileKeys.EMERGENCY_HISTORY] = profile.emergencyHistory.joinToString("|||")
            prefs[ProfileKeys.DIET_PLAN] = profile.dietPlan
            prefs[ProfileKeys.PURPOSE] = profile.purpose

            // FIX: now actually written
            prefs[ProfileKeys.INSULIN_TYPE] = profile.insulinType ?: ""
            prefs[ProfileKeys.OTHER_CONDITIONS] = profile.otherConditions.joinToString("|||")
        }
    }

    suspend fun saveFacts(facts: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[ProfileKeys.KNOWN_FACTS] = facts.joinToString("|||")
        }
    }
}
package com.sugarsaathi.app

/**
 * Backup file schema (JSON).
 *
 * The envelope carries a version number and an app version so a future app
 * release can migrate an older backup even if the shape has drifted. Every
 * data class here has default values, so a file missing a section imports
 * cleanly instead of crashing the parser - a robustness the patient will
 * never see but which matters if we ever change the shape.
 *
 * DELIBERATELY EXCLUDED:
 *   - Chat history. Contains free-text worries and symptoms the patient may
 *     not want anyone else reading. A JSON file in shared Downloads storage
 *     is readable by anything the patient shares the phone with; keeping
 *     chat out of the backup is a hard privacy boundary, not a nice-to-have.
 *     Not "we can add it later" - a separate export with its own warning if
 *     ever needed.
 *   - Reminders. Restorable from user preference, not clinical data.
 *   - Consent timestamps. Bound to the account, not portable data.
 *   - Auth tokens. Firebase-scoped, meaningless in another install.
 *
 * The Med and Hba1cItem shapes mirror the Room entities exactly on the fields
 * that matter clinically. Room primary keys are NOT copied - a merge on
 * import must not collide with existing IDs, and a fresh id is assigned to
 * each imported row.
 */
const val BACKUP_SCHEMA_VERSION = 1

data class BackupEnvelope(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val appVersion: String = "",
    val exportedAt: Long = 0L,
    val profile: BackupProfile? = null,
    val glucose: List<BackupGlucose> = emptyList(),
    val medications: List<BackupMed> = emptyList(),
    val hba1c: List<BackupHba1c> = emptyList()
)

/**
 * Profile snapshot. Doctor phone and emergency contact are included because
 * they are useful to a patient restoring on a new phone - but a shared backup
 * file will carry them too, which is why the export button warns.
 */
data class BackupProfile(
    val name: String = "",
    val age: Int = 0,
    val sex: String = "",
    val country: String = "",
    val diabetesType: String = "",
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
    val knownFacts: List<String> = emptyList(),
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    val smokingStatus: String = "",
    val occupation: String = "",
    val educationLevel: String = "",
    val activityLevel: String = "",
    val treatmentApproach: List<String> = emptyList(),
    val monitoringMethod: String = "",
    val emergencyHistory: List<String> = emptyList(),
    val dietPlan: String = "",
    val purpose: String = "patient",
    val allergies: List<String> = emptyList(),
    val hba1cDate: String? = null,
    val doctorName: String = "",
    val doctorPhone: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = ""
)

data class BackupGlucose(
    val value: Float,
    val unit: String,
    val readingType: String,
    val timestamp: Long,
    val note: String = ""
)

data class BackupMed(
    val name: String,
    val dose: String = "",
    val frequency: String = "",
    val timing: String = "",
    val timesOfDay: String = "",
    val notes: String = "",
    val isInsulin: Boolean = false,
    val active: Boolean = true,
    val createdAt: Long = 0L
)

data class BackupHba1c(
    val value: Float,
    val timestamp: Long,
    val source: String = "lab",
    val note: String = ""
)

/**
 * Result of parsing a backup file, used to render the preview dialog before
 * the patient chooses Merge or Replace.
 */
data class BackupPreview(
    val glucoseCount: Int,
    val medicationCount: Int,
    val hba1cCount: Int,
    val hasProfile: Boolean,
    val exportedAt: Long,
    val schemaVersion: Int
)
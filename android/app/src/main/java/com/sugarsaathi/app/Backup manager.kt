package com.sugarsaathi.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manual backup and restore for GlycoAI's clinical data.
 *
 * Uses org.json rather than a serialization library on purpose: no extra
 * dependency, no annotation setup, and the shape here is small enough that
 * hand-writing the JSON is safer than trusting reflection to produce a stable
 * file format. If a field is renamed in a data class, the JSON key here
 * doesn't change silently - the compiler stays out of it, so the file format
 * is genuinely stable.
 *
 * Chat history is deliberately absent from every function in this file. There
 * is no toggle to include it. Making that omission structural, not
 * configurable, is the point.
 */
object BackupManager {

    private const val FILENAME_PREFIX = "glycoai_backup_"
    private const val FILENAME_EXT = "json"
    private const val MIME_TYPE = "application/json"

    // ---------- EXPORT ----------

    /**
     * Builds an envelope from the app's live state.
     *
     * Runs at the moment the button is pressed, not periodically - manual
     * backup only, per design. All reads are one-shot (getAllOnce, first()) so
     * we don't accidentally leak a flow subscription past the export.
     */
    suspend fun buildEnvelope(context: Context): BackupEnvelope {
        val db = AppDatabase.getInstance(context)
        val profileRepo = ProfileRepository(context)

        val profile = profileRepo.profileFlow.first()
        val glucose = db.glucoseDao().getAllOnce()
        val meds = db.medicationDao().getAllOnce()
        val hba1c = db.hba1cDao().getAll().first()

        return BackupEnvelope(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            appVersion = readAppVersion(context),
            exportedAt = System.currentTimeMillis(),
            profile = profile.toBackup(),
            glucose = glucose.map { it.toBackup() },
            medications = meds.map { it.toBackup() },
            hba1c = hba1c.map { it.toBackup() }
        )
    }

    /**
     * Writes the envelope to a JSON file and returns a shareable Uri.
     *
     * On Android 10+ (Q, API 29) uses MediaStore to drop the file in the
     * public Downloads folder without needing WRITE_EXTERNAL_STORAGE. On
     * older versions falls back to the app's own external files directory
     * and shares via FileProvider - still readable by other apps but scoped.
     */
    fun writeToDownloads(context: Context, envelope: BackupEnvelope): Uri? {
        val json = envelopeToJson(envelope).toString(2)
        val filename = "$FILENAME_PREFIX${todayStamp()}.$FILENAME_EXT"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(context, filename, json)
            } else {
                writeViaFileProvider(context, filename, json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun writeViaMediaStore(context: Context, filename: String, json: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: return null

        resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        return uri
    }

    private fun writeViaFileProvider(context: Context, filename: String, json: String): Uri {
        val dir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
        val file = File(dir, filename)
        file.writeText(json)
        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
    }

    /**
     * Fires the Android share sheet so the patient can send the file to
     * Drive, WhatsApp, email, etc. We DO NOT auto-upload anywhere - "manual
     * only" means the patient controls where it goes.
     */
    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Save backup").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ---------- PREVIEW / IMPORT ----------

    /**
     * Reads a file the patient picked and returns its envelope + a preview.
     * Returns null on any parse failure - a broken file must never partially
     * import, or the patient ends up with a corrupted state that mixes old
     * and new data unpredictably.
     */
    fun parse(context: Context, uri: Uri): Pair<BackupEnvelope, BackupPreview>? = try {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        } ?: return null

        val envelope = jsonToEnvelope(JSONObject(text))
        val preview = BackupPreview(
            glucoseCount = envelope.glucose.size,
            medicationCount = envelope.medications.size,
            hba1cCount = envelope.hba1c.size,
            hasProfile = envelope.profile != null,
            exportedAt = envelope.exportedAt,
            schemaVersion = envelope.schemaVersion
        )
        envelope to preview
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /**
     * Applies the envelope.
     *
     * MERGE:
     *   - Glucose readings: added by timestamp - if a reading with the same
     *     millisecond timestamp already exists, it's assumed to be the same
     *     one and skipped. Not a perfect dedup but close enough - two real
     *     readings entered on the same millisecond is not physically
     *     realistic.
     *   - Medications: added if no active medicine with the same name
     *     already exists. Names are case-insensitive.
     *   - HbA1c: added by timestamp, same rule as glucose.
     *   - Profile: NOT touched on merge - the current profile stays. A merged
     *     restore is for adding data, not overwriting who the patient is.
     *
     * REPLACE:
     *   - Wipes each table, then inserts. Profile IS overwritten.
     *   - Auth state (Firebase) is untouched - the patient stays signed in.
     *   - Chat history is untouched too (this manager never reads it, so no
     *     wipe path exists for it either).
     */
    suspend fun apply(
        context: Context,
        envelope: BackupEnvelope,
        merge: Boolean
    ): ImportResult {
        val db = AppDatabase.getInstance(context)
        val profileRepo = ProfileRepository(context)

        var glucoseAdded = 0
        var medsAdded = 0
        var hba1cAdded = 0

        if (!merge) {
            // Order matters: children before parents if there were foreign
            // keys. There aren't any here, but keeping a stable order makes
            // future changes safer.
            db.glucoseDao().deleteAll()
            db.medicationDao().deleteAll()
            db.hba1cDao().deleteAll()
        }

        val existingGlucoseTimestamps = if (merge)
            db.glucoseDao().getAllOnce().map { it.timestamp }.toHashSet()
        else emptySet()

        val existingMedNames = if (merge)
            db.medicationDao().getAllOnce()
                .filter { it.active }
                .map { it.name.trim().lowercase() }.toHashSet()
        else emptySet()

        val existingHba1cTimestamps = if (merge)
            db.hba1cDao().getAll().first().map { it.timestamp }.toHashSet()
        else emptySet()

        envelope.glucose.forEach { g ->
            if (merge && g.timestamp in existingGlucoseTimestamps) return@forEach
            db.glucoseDao().insert(g.toEntity())
            glucoseAdded++
        }

        envelope.medications.forEach { m ->
            if (merge && m.name.trim().lowercase() in existingMedNames) return@forEach
            db.medicationDao().insert(m.toEntity())
            medsAdded++
        }

        envelope.hba1c.forEach { h ->
            if (merge && h.timestamp in existingHba1cTimestamps) return@forEach
            db.hba1cDao().insert(h.toEntity())
            hba1cAdded++
        }

        // Profile only replaced in REPLACE mode. Merge preserves who the
        // patient is - the file is treated as data to add, not identity to
        // overwrite. Onboarding state is inherited from whatever the profile
        // now is; a replace of a fresh install must NOT drop the patient
        // back to onboarding.
        if (!merge && envelope.profile != null) {
            val current = profileRepo.profileFlow.first()
            profileRepo.saveProfile(
                envelope.profile.toUserProfileData(
                    onboardingDone = current.onboardingDone || envelope.profile.name.isNotBlank(),
                    consentAccepted = current.consentAccepted,
                    consentTimestamp = current.consentTimestamp
                )
            )
        }

        return ImportResult(glucoseAdded, medsAdded, hba1cAdded, replacedProfile = !merge)
    }

    data class ImportResult(
        val glucoseAdded: Int,
        val medsAdded: Int,
        val hba1cAdded: Int,
        val replacedProfile: Boolean
    )

    // ---------- JSON adapters (hand-written, on purpose) ----------

    private fun envelopeToJson(e: BackupEnvelope): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", e.schemaVersion)
        root.put("appVersion", e.appVersion)
        root.put("exportedAt", e.exportedAt)
        e.profile?.let { root.put("profile", profileToJson(it)) }
        root.put("glucose", JSONArray().apply {
            e.glucose.forEach { put(glucoseToJson(it)) }
        })
        root.put("medications", JSONArray().apply {
            e.medications.forEach { put(medToJson(it)) }
        })
        root.put("hba1c", JSONArray().apply {
            e.hba1c.forEach { put(hba1cToJson(it)) }
        })
        return root
    }

    private fun jsonToEnvelope(o: JSONObject): BackupEnvelope {
        return BackupEnvelope(
            schemaVersion = o.optInt("schemaVersion", 0),
            appVersion = o.optString("appVersion"),
            exportedAt = o.optLong("exportedAt", 0L),
            profile = o.optJSONObject("profile")?.let(::jsonToProfile),
            glucose = o.optJSONArray("glucose")?.let(::jsonToGlucoseList) ?: emptyList(),
            medications = o.optJSONArray("medications")?.let(::jsonToMedList) ?: emptyList(),
            hba1c = o.optJSONArray("hba1c")?.let(::jsonToHba1cList) ?: emptyList()
        )
    }

    private fun profileToJson(p: BackupProfile) = JSONObject().apply {
        put("name", p.name); put("age", p.age); put("sex", p.sex)
        put("country", p.country); put("diabetesType", p.diabetesType)
        p.diagnosisYear?.let { put("diagnosisYear", it) }
        p.insulinType?.let { put("insulinType", it) }
        put("medications", JSONArray(p.medications))
        p.glucoseMonitoring?.let { put("glucoseMonitoring", it) }
        p.severeHypoglycemia?.let { put("severeHypoglycemia", it) }
        put("otherConditions", JSONArray(p.otherConditions))
        p.hba1c?.let { put("hba1c", it.toDouble()) }
        put("complications", JSONArray(p.complications))
        put("language", p.language); put("responseStyle", p.responseStyle)
        put("glucoseUnit", p.glucoseUnit)
        put("knownFacts", JSONArray(p.knownFacts))
        p.weightKg?.let { put("weightKg", it.toDouble()) }
        p.heightCm?.let { put("heightCm", it.toDouble()) }
        put("smokingStatus", p.smokingStatus)
        put("occupation", p.occupation)
        put("educationLevel", p.educationLevel)
        put("activityLevel", p.activityLevel)
        put("treatmentApproach", JSONArray(p.treatmentApproach))
        put("monitoringMethod", p.monitoringMethod)
        put("emergencyHistory", JSONArray(p.emergencyHistory))
        put("dietPlan", p.dietPlan)
        put("purpose", p.purpose)
        put("allergies", JSONArray(p.allergies))
        p.hba1cDate?.let { put("hba1cDate", it) }
        put("doctorName", p.doctorName)
        put("doctorPhone", p.doctorPhone)
        put("emergencyContactName", p.emergencyContactName)
        put("emergencyContactPhone", p.emergencyContactPhone)
    }

    private fun jsonToProfile(o: JSONObject) = BackupProfile(
        name = o.optString("name"), age = o.optInt("age", 0),
        sex = o.optString("sex"), country = o.optString("country"),
        diabetesType = o.optString("diabetesType"),
        diagnosisYear = o.optStringOrNull("diagnosisYear"),
        insulinType = o.optStringOrNull("insulinType"),
        medications = o.optJSONArray("medications").toStringList(),
        glucoseMonitoring = o.optStringOrNull("glucoseMonitoring"),
        severeHypoglycemia = o.optStringOrNull("severeHypoglycemia"),
        otherConditions = o.optJSONArray("otherConditions").toStringList(),
        hba1c = o.optDoubleOrNull("hba1c")?.toFloat(),
        complications = o.optJSONArray("complications").toStringList(),
        language = o.optString("language", "en"),
        responseStyle = o.optString("responseStyle", "simple"),
        glucoseUnit = o.optString("glucoseUnit", "mg/dL"),
        knownFacts = o.optJSONArray("knownFacts").toStringList(),
        weightKg = o.optDoubleOrNull("weightKg")?.toFloat(),
        heightCm = o.optDoubleOrNull("heightCm")?.toFloat(),
        smokingStatus = o.optString("smokingStatus"),
        occupation = o.optString("occupation"),
        educationLevel = o.optString("educationLevel"),
        activityLevel = o.optString("activityLevel"),
        treatmentApproach = o.optJSONArray("treatmentApproach").toStringList(),
        monitoringMethod = o.optString("monitoringMethod"),
        emergencyHistory = o.optJSONArray("emergencyHistory").toStringList(),
        dietPlan = o.optString("dietPlan"),
        purpose = o.optString("purpose", "patient"),
        allergies = o.optJSONArray("allergies").toStringList(),
        hba1cDate = o.optStringOrNull("hba1cDate"),
        doctorName = o.optString("doctorName"),
        doctorPhone = o.optString("doctorPhone"),
        emergencyContactName = o.optString("emergencyContactName"),
        emergencyContactPhone = o.optString("emergencyContactPhone")
    )

    private fun glucoseToJson(g: BackupGlucose) = JSONObject().apply {
        put("value", g.value.toDouble())
        put("unit", g.unit)
        put("readingType", g.readingType)
        put("timestamp", g.timestamp)
        put("note", g.note)
    }

    private fun jsonToGlucoseList(a: JSONArray): List<BackupGlucose> =
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            BackupGlucose(
                value = o.getDouble("value").toFloat(),
                unit = o.optString("unit", "mg/dL"),
                readingType = o.optString("readingType"),
                timestamp = o.getLong("timestamp"),
                note = o.optString("note")
            )
        }

    private fun medToJson(m: BackupMed) = JSONObject().apply {
        put("name", m.name); put("dose", m.dose)
        put("frequency", m.frequency); put("timing", m.timing)
        put("timesOfDay", m.timesOfDay); put("notes", m.notes)
        put("isInsulin", m.isInsulin); put("active", m.active)
        put("createdAt", m.createdAt)
    }

    private fun jsonToMedList(a: JSONArray): List<BackupMed> =
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            BackupMed(
                name = o.getString("name"),
                dose = o.optString("dose"),
                frequency = o.optString("frequency"),
                timing = o.optString("timing"),
                timesOfDay = o.optString("timesOfDay"),
                notes = o.optString("notes"),
                isInsulin = o.optBoolean("isInsulin", false),
                active = o.optBoolean("active", true),
                createdAt = o.optLong("createdAt", 0L)
            )
        }

    private fun hba1cToJson(h: BackupHba1c) = JSONObject().apply {
        put("value", h.value.toDouble())
        put("timestamp", h.timestamp)
        put("source", h.source)
        put("note", h.note)
    }

    private fun jsonToHba1cList(a: JSONArray): List<BackupHba1c> =
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            BackupHba1c(
                value = o.getDouble("value").toFloat(),
                timestamp = o.getLong("timestamp"),
                source = o.optString("source", "lab"),
                note = o.optString("note")
            )
        }

    // ---------- Small helpers ----------

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).ifBlank { null } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONArray?.toStringList(): List<String> =
        this?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
        } ?: emptyList()

    private fun todayStamp(): String =
        SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())

    private fun readAppVersion(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: ""
    } catch (_: Exception) { "" }
}

// ---------- Entity <-> BackupItem mappers ----------
// Kept as extension functions on the entity types so a change to the entity
// shape triggers a compile error here immediately, rather than a silent JSON
// drift no one notices until a restore fails.

private fun UserProfileData.toBackup(): BackupProfile = BackupProfile(
    name = name, age = age, sex = sex, country = country,
    diabetesType = diabetesType, diagnosisYear = diagnosisYear,
    insulinType = insulinType, medications = medications,
    glucoseMonitoring = glucoseMonitoring, severeHypoglycemia = severeHypoglycemia,
    otherConditions = otherConditions, hba1c = hba1c,
    complications = complications, language = language,
    responseStyle = responseStyle, glucoseUnit = glucoseUnit,
    knownFacts = knownFacts, weightKg = weightKg, heightCm = heightCm,
    smokingStatus = smokingStatus, occupation = occupation,
    educationLevel = educationLevel, activityLevel = activityLevel,
    treatmentApproach = treatmentApproach, monitoringMethod = monitoringMethod,
    emergencyHistory = emergencyHistory, dietPlan = dietPlan,
    purpose = purpose, allergies = allergies, hba1cDate = hba1cDate,
    doctorName = doctorName, doctorPhone = doctorPhone,
    emergencyContactName = emergencyContactName,
    emergencyContactPhone = emergencyContactPhone
)

private fun BackupProfile.toUserProfileData(
    onboardingDone: Boolean,
    consentAccepted: Boolean,
    consentTimestamp: Long
): UserProfileData = UserProfileData(
    name = name, age = age, sex = sex, country = country,
    diabetesType = diabetesType, diagnosisYear = diagnosisYear,
    insulinType = insulinType, medications = medications,
    glucoseMonitoring = glucoseMonitoring, severeHypoglycemia = severeHypoglycemia,
    otherConditions = otherConditions, hba1c = hba1c,
    complications = complications, language = language,
    responseStyle = responseStyle, glucoseUnit = glucoseUnit,
    onboardingDone = onboardingDone, knownFacts = knownFacts,
    weightKg = weightKg, heightCm = heightCm,
    smokingStatus = smokingStatus, occupation = occupation,
    educationLevel = educationLevel,
    usedChatbotBefore = "",
    activityLevel = activityLevel, treatmentApproach = treatmentApproach,
    monitoringMethod = monitoringMethod, emergencyHistory = emergencyHistory,
    dietPlan = dietPlan, purpose = purpose,
    consentAccepted = consentAccepted, consentTimestamp = consentTimestamp,
    allergies = allergies, hba1cDate = hba1cDate,
    doctorName = doctorName, doctorPhone = doctorPhone,
    emergencyContactName = emergencyContactName,
    emergencyContactPhone = emergencyContactPhone
)

private fun GlucoseReading.toBackup() = BackupGlucose(
    value = value, unit = unit, readingType = readingType,
    timestamp = timestamp, note = note
)

private fun BackupGlucose.toEntity() = GlucoseReading(
    id = 0,
    value = value, unit = unit, readingType = readingType,
    timestamp = timestamp, note = note
)

private fun Medication.toBackup() = BackupMed(
    name = name, dose = dose, frequency = frequency, timing = timing,
    timesOfDay = timesOfDay, notes = notes, isInsulin = isInsulin,
    active = active, createdAt = createdAt
)

private fun BackupMed.toEntity() = Medication(
    id = 0,
    name = name, dose = dose, frequency = frequency, timing = timing,
    timesOfDay = timesOfDay, notes = notes, isInsulin = isInsulin,
    active = active, createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt
)

private fun Hba1cEntry.toBackup() = BackupHba1c(
    value = value, timestamp = timestamp, source = source, note = note
)

private fun BackupHba1c.toEntity() = Hba1cEntry(
    id = 0, value = value, timestamp = timestamp, source = source, note = note
)
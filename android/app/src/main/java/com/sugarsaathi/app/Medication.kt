package com.sugarsaathi.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How often a medicine is taken. Stored by NAME so the enum can be reordered
 * without silently reassigning existing rows.
 */
enum class MedFrequency(val labelRes: Int) {
    ONCE_DAILY(R.string.med_freq_once),
    TWICE_DAILY(R.string.med_freq_twice),
    THREE_TIMES(R.string.med_freq_three),
    FOUR_TIMES(R.string.med_freq_four),
    AS_NEEDED(R.string.med_freq_as_needed),
    WEEKLY(R.string.med_freq_weekly),
    OTHER(R.string.med_freq_other);

    companion object {
        fun fromName(v: String) = entries.firstOrNull { it.name == v } ?: ONCE_DAILY
    }
}

/** Whether it is taken with food. Affects several common diabetes medicines. */
enum class MedTiming(val labelRes: Int) {
    BEFORE_MEAL(R.string.med_timing_before),
    WITH_MEAL(R.string.med_timing_with),
    AFTER_MEAL(R.string.med_timing_after),
    BEDTIME(R.string.med_timing_bedtime),
    ANY_TIME(R.string.med_timing_any);

    companion object {
        fun fromName(v: String) = entries.firstOrNull { it.name == v } ?: ANY_TIME
    }
}

/**
 * One medicine the patient takes.
 *
 * REPLACES NOTHING: UserProfileData.medications (a plain list of names) is kept
 * as-is and still sent to the backend. This table adds the detail that list
 * cannot hold. Existing names are imported here once, so nobody loses what they
 * already entered during onboarding.
 *
 * `dose` is deliberately free text ("500mg", "10 units", "half tablet").
 * Forcing a number and a unit would exclude the many patients who know their
 * medicine only as "the white tablet, two a day".
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** Free text. Empty when the patient does not know it. */
    val dose: String = "",

    /** MedFrequency name. */
    val frequency: String = MedFrequency.ONCE_DAILY.name,

    /** MedTiming name. */
    val timing: String = MedTiming.ANY_TIME.name,

    /** Free text, e.g. "morning and night" or "before breakfast". */
    val timesOfDay: String = "",

    /** True when it is an insulin. Drives stricter handling downstream. */
    val isInsulin: Boolean = false,

    val notes: String = "",

    /** Stopped medicines are kept, not deleted - history matters clinically. */
    val active: Boolean = true,

    val createdAt: Long = System.currentTimeMillis()
) {
    val frequencyEnum: MedFrequency get() = MedFrequency.fromName(frequency)
    val timingEnum: MedTiming get() = MedTiming.fromName(timing)

    /** One-line description for the backend, e.g. "Glucophage 500mg, twice daily, with meals". */
    fun toContextLine(
        freqLabel: String,
        timingLabel: String
    ): String = buildString {
        append(name)
        if (dose.isNotBlank()) append(" $dose")
        append(" — ").append(freqLabel)
        if (timingEnum != MedTiming.ANY_TIME) append(", ").append(timingLabel)
        if (timesOfDay.isNotBlank()) append(" (").append(timesOfDay).append(")")
        if (isInsulin) append(" [insulin]")
    }
}
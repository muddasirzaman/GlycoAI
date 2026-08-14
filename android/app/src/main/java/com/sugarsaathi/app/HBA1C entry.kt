package com.sugarsaathi.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single HbA1c reading. History is a small list appended to over months, so
 * a plain autoincrement table is enough - no complex relationships.
 *
 * timestamp is the DATE OF THE TEST (as reported by the patient), not when the
 * entry was created. A lab result entered a week late must still sort by the
 * day it was measured, so trend arrows reflect physiology and not typing.
 *
 * source distinguishes:
 *   "lab"       - the patient typed a value from a real lab report
 *   "estimated" - the app calculated it (e.g. from CGM data). NEVER prescribed,
 *                 NEVER shown as clinical truth, always visually distinct in
 *                 the UI. Reserved for future use; today we only store "lab".
 *
 * A hard rule the whole HbA1c screen depends on: "estimated" entries must be
 * clearly labelled and rendered differently so a patient can never mistake a
 * calculated number for their real lab result. See Hba1cHistoryScreen.
 */
@Entity(tableName = "hba1c_entries")
data class Hba1cEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val value: Float,
    val timestamp: Long,        // date of the test in millis
    val source: String = "lab", // "lab" or "estimated"
    val note: String = ""
)
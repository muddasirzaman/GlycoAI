package com.sugarsaathi.app

import android.content.Context

/**
 * Builds the medication block sent to the AI.
 *
 * Deliberately NOT part of any screen: context selection is pipeline logic, and
 * keeping it here means the rule "what gets sent" is reviewable in one file
 * rather than scattered through UI code.
 *
 * WHAT THIS DOES AND DOES NOT ENABLE
 *
 * Knowing the dose lets the assistant be specific about WHAT the patient takes
 * ("I can see you're on Glucophage 500mg twice daily"). It does NOT let it
 * advise on doses - the backend refuses those regardless of how much detail it
 * has, and more context never unlocks dose advice. That boundary is enforced in
 * check_safety() on the server, not left to the model's judgement.
 */
object MedicationContext {

    /**
     * One line per active medicine, or null when there are none - null keeps
     * the block out of the prompt entirely rather than sending "None".
     */
    suspend fun buildSummary(context: Context): String? {
        val dao = AppDatabase.getInstance(context).medicationDao()
        val active = dao.getActiveOnce()
        if (active.isEmpty()) return null

        return active.joinToString("\n") { med ->
            "- " + med.toContextLine(
                freqLabel = context.getString(med.frequencyEnum.labelRes),
                timingLabel = context.getString(med.timingEnum.labelRes)
            ) + if (med.notes.isNotBlank()) " · ${med.notes}" else ""
        }
    }

    /**
     * Plain names only, for the existing `medications` field. Kept so the
     * backend's medication keyword rules and the older prompt sections keep
     * working exactly as before - this ADDS detail, it does not replace what
     * was already there.
     */
    suspend fun activeNames(context: Context): List<String> =
        AppDatabase.getInstance(context).medicationDao()
            .getActiveOnce()
            .map { it.name }
}
package com.sugarsaathi.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Hba1cViewModel : ViewModel() {

    private var dao: Hba1cDao? = null
    private var profileRepo: ProfileRepository? = null

    private val _entries = MutableStateFlow<List<Hba1cEntry>>(emptyList())
    val entries: StateFlow<List<Hba1cEntry>> = _entries

    fun init(context: Context) {
        if (dao == null) {
            dao = AppDatabase.getInstance(context).hba1cDao()
            profileRepo = ProfileRepository(context)
            observe()
            // First-open backfill. Runs once, and only if the table is empty
            // AND the profile has a value to seed. See backfillFromProfile.
            viewModelScope.launch { backfillFromProfile() }
        }
    }

    private fun observe() {
        viewModelScope.launch {
            dao?.getAll()?.collect { list -> _entries.value = list }
        }
    }

    /**
     * Seeds the HbA1c table from profile.hba1c if the table has never had an
     * entry.
     *
     * The value on the profile came from onboarding, but the table is where
     * the HbA1c screen reads from - so without this, a patient who entered
     * their HbA1c months ago would open the screen and see "no entries" while
     * the Diabetes profile section still shows their value. Confusing, and it
     * hides history that already existed.
     *
     * Only runs when BOTH conditions hold:
     *   - the table is currently empty
     *   - the profile has an hba1c value to seed from
     *
     * The seeded entry uses profile.hba1cDate when present, parsed by the same
     * "d MMM yyyy" format the app writes elsewhere, or falls back to the
     * current time so the entry at least exists. That fallback is imperfect
     * (an entry dated today when the test was months ago will distort a future
     * trend arrow) but is strictly better than losing the value entirely.
     *
     * Idempotent by design: on the second launch the table is no longer empty
     * so nothing happens, even if the patient deleted the seeded entry - we do
     * not resurrect deleted history.
     */
    private suspend fun backfillFromProfile() {
        val d = dao ?: return
        val repo = profileRepo ?: return

        // Cheap: getLatestOnce returns null on empty table without loading it.
        if (d.getLatestOnce() != null) return

        val profile = repo.profileFlow.first()
        val value = profile.hba1c ?: return

        val timestamp = parseStoredDate(profile.hba1cDate) ?: System.currentTimeMillis()

        d.insert(
            Hba1cEntry(
                value = value,
                timestamp = timestamp,
                source = "lab",
                note = "" // No note field to seed from - kept blank rather
                // than adding a "seeded from profile" tag the
                // patient never wrote.
            )
        )
        // Not calling syncLatestToProfile() here. The profile is where the
        // value came FROM - re-writing the same value back would just churn
        // the DataStore.
    }

    /**
     * Saves a new HbA1c entry and keeps the profile fields in sync with the
     * most recent one.
     *
     * profile.hba1c and profile.hba1cDate are what the backend prompt reads,
     * so every save has to update them - otherwise the model would keep citing
     * a stale reading from onboarding while the patient is looking at a fresh
     * number in the app. That mismatch is exactly the kind of thing that erodes
     * trust in the assistant.
     *
     * Uses getLatestOnce() rather than assuming the just-inserted value is now
     * the latest - a patient entering an old lab result from months ago should
     * NOT overwrite a more recent entry in the profile. The date on the entry,
     * not the time of insertion, decides what "latest" means.
     */
    fun save(
        value: Float,
        testDateMillis: Long,
        note: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            dao?.insert(
                Hba1cEntry(
                    value = value,
                    timestamp = testDateMillis,
                    source = "lab",
                    note = note
                )
            )
            syncLatestToProfile()
            onDone()
        }
    }

    fun delete(entry: Hba1cEntry) {
        viewModelScope.launch {
            dao?.delete(entry)
            syncLatestToProfile()
        }
    }

    private suspend fun syncLatestToProfile() {
        val repo = profileRepo ?: return
        val latest = dao?.getLatestOnce()
        val current = repo.profileFlow.first()

        val newHba1c = latest?.value
        val newDate = latest?.let { formatDate(it.timestamp) }

        // Only write when something actually changed. Every write to DataStore
        // emits a new profile downstream and triggers recomposition all the
        // way up in MainActivity - firing that on every irrelevant insert
        // would churn the UI for no reason.
        if (current.hba1c != newHba1c || current.hba1cDate != newDate) {
            repo.saveProfile(
                current.copy(
                    hba1c = newHba1c,
                    hba1cDate = newDate
                )
            )
        }
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

    /**
     * Parses the profile's stored hba1cDate back into millis.
     *
     * The profile stores the date as free text ("d MMM yyyy" is the app's
     * convention, but onboarding lets the patient type anything). Try the
     * canonical format first, then a few common human variants people
     * actually type. Returns null on failure so backfillFromProfile can
     * fall back to now() rather than crashing.
     */
    private fun parseStoredDate(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val patterns = listOf("d MMM yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "MMMM yyyy", "MMM yyyy")
        for (p in patterns) {
            try {
                return SimpleDateFormat(p, Locale.getDefault())
                    .apply { isLenient = false }
                    .parse(text.trim())?.time
            } catch (_: Exception) { /* try next pattern */ }
        }
        return null
    }
}
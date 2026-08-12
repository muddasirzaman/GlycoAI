package com.sugarsaathi.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MedicationViewModel : ViewModel() {

    private var dao: MedicationDao? = null

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications

    fun init(context: Context) {
        if (dao == null) {
            dao = AppDatabase.getInstance(context).medicationDao()
            observe()
            importLegacyNamesIfEmpty(context)
        }
    }

    private fun observe() {
        viewModelScope.launch {
            dao?.getAll()?.collect { _medications.value = it }
        }
    }

    /**
     * Brings forward the medicine NAMES a patient entered during onboarding.
     *
     * Runs only when the table is empty, so it cannot duplicate entries or
     * resurrect something the patient deliberately deleted. Dose and timing are
     * left blank - the patient fills those in, and blank is honest rather than
     * guessed.
     */
    private fun importLegacyNamesIfEmpty(context: Context) {
        viewModelScope.launch {
            val d = dao ?: return@launch
            if (d.count() > 0) return@launch

            val profile = ProfileRepository(context).profileFlow.first()
            profile.medications
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("None", ignoreCase = true) }
                .distinct()
                .forEach { name ->
                    d.insert(
                        Medication(
                            name = name,
                            // Best-effort flag; the patient can correct it.
                            isInsulin = looksLikeInsulin(name)
                        )
                    )
                }
        }
    }

    fun save(medication: Medication) {
        viewModelScope.launch {
            if (medication.id == 0L) dao?.insert(medication)
            else dao?.update(medication)
        }
    }

    fun setActive(medication: Medication, active: Boolean) {
        viewModelScope.launch { dao?.setActive(medication.id, active) }
    }

    fun delete(medication: Medication) {
        viewModelScope.launch { dao?.delete(medication) }
    }

    companion object {
        // Brands common in Pakistan plus the generic stems. Only a hint - the
        // patient can toggle it, and nothing safety-critical depends on it
        // being right.
        private val INSULIN_HINTS = listOf(
            "insulin", "mixtard", "lantus", "novorapid", "humalog", "actrapid",
            "insulatard", "levemir", "novomix", "humulin", "toujeo", "tresiba",
            "apidra", "ryzodeg"
        )

        fun looksLikeInsulin(name: String): Boolean {
            val n = name.lowercase()
            return INSULIN_HINTS.any { it in n }
        }
    }
}

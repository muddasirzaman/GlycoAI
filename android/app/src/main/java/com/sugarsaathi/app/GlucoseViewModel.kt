package com.sugarsaathi.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GlucoseSummary(
    val average: Int,
    val highest: Int,
    val lowest: Int,
    val count: Int,
    val inRangePercent: Int
)


class GlucoseViewModel : ViewModel() {

    private var dao: GlucoseDao? = null

    private val _readings = MutableStateFlow<List<GlucoseReading>>(emptyList())
    val readings: StateFlow<List<GlucoseReading>> = _readings

    // Call once, from the screen, to connect the database
    fun init(context: Context) {
        if (dao == null) {
            dao = AppDatabase.getInstance(context).glucoseDao()
            observeReadings()
        }
    }

    private fun observeReadings() {
        viewModelScope.launch {
            dao?.getAllReadings()?.collect { list ->
                _readings.value = list
            }
        }
    }

    // Save a new reading. Returns true if the input was valid and saved.
    fun addReading(
        value: Float,
        unit: String,
        readingType: String,
        note: String
    ): Boolean {
        // Feature 9 validation — physiological range for mg/dL
        val validRange = if (unit == "mmol/L") value in 1.0f..40.0f
        else value in 20.0f..700.0f

        if (!validRange) return false

        val reading = GlucoseReading(
            value = value,
            unit = unit,
            readingType = readingType,
            timestamp = System.currentTimeMillis(),
            note = note
        )

        viewModelScope.launch {
            dao?.insert(reading)
        }
        return true
    }

    fun deleteReading(reading: GlucoseReading) {
        viewModelScope.launch {
            dao?.delete(reading)
        }
    }

    // Summary of the last 7 days
    fun sevenDaySummary(): GlucoseSummary {
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)

        val recent = _readings.value.filter { it.timestamp >= sevenDaysAgo }

        if (recent.isEmpty()) {
            return GlucoseSummary(0, 0, 0, 0, 0)
        }

        // Normalize everything to mg/dL so the math is consistent
        val values = recent.map {
            if (it.unit == "mmol/L") it.value * 18f else it.value
        }

        val avg = values.average().toInt()
        val high = values.max().toInt()
        val low = values.min().toInt()

        val inRange = values.count { it in 70f..180f }
        val inRangePct = (inRange * 100) / values.size

        return GlucoseSummary(
            average = avg,
            highest = high,
            lowest = low,
            count = recent.size,
            inRangePercent = inRangePct
        )
    }

    // Compact summary for the AI. Returns null if there's nothing useful.
    fun glucoseSummaryForAI(): String? {
        val all = _readings.value
        if (all.isEmpty()) return null

        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)
        val recent = all.filter { it.timestamp >= sevenDaysAgo }

        if (recent.isEmpty()) return "No glucose readings logged in the last 7 days."

        val unit = recent.first().unit
        val values = recent.map { if (it.unit == "mmol/L") it.value * 18f else it.value }

        val avg = values.average().toInt()
        val high = values.max().toInt()
        val low = values.min().toInt()
        val inRange = values.count { it in 70f..180f }
        val inRangePct = (inRange * 100) / values.size
        val highCount = values.count { it > 180f }
        val lowCount = values.count { it < 70f }

        // Most recent reading with how long ago
        val latest = all.maxByOrNull { it.timestamp }!!
        val hoursAgo = ((now - latest.timestamp) / (1000 * 60 * 60)).toInt()
        val agoText = when {
            hoursAgo < 1 -> "less than an hour ago"
            hoursAgo < 24 -> "$hoursAgo hours ago"
            else -> "${hoursAgo / 24} days ago"
        }

        return buildString {
            append("Last 7 days: ${recent.size} readings, ")
            append("average $avg mg/dL, ")
            append("highest $high, lowest $low, ")
            append("$inRangePct% in target range (70-180). ")
            if (highCount > 0) append("$highCount reading(s) above 180. ")
            if (lowCount > 0) append("$lowCount reading(s) below 70. ")
            append("Most recent: ${latest.value.toInt()} $unit ")
            append("(${latest.readingType}) $agoText.")
            if (latest.note.isNotEmpty()) append(" Note: ${latest.note}.")
        }
    }
}
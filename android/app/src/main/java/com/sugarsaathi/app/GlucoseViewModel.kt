package com.sugarsaathi.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class GlucoseSummary(
    val average: Int,
    val highest: Int,
    val lowest: Int,
    val count: Int,
    val inRangePercent: Int
)

/**
 * Fuller dashboard breakdown for the History tab. Extends GlucoseSummary with
 * the numbers a diabetic actually reads first: how much time above range, how
 * much below, and — when the underlying readings carry a readingType tag —
 * split averages for fasting and post-meal contexts.
 *
 * Every number is derived in mg/dL to keep the maths comparable across users
 * who log in mmol/L. If no readings fall inside the window the counts are all
 * zero and callers should render the empty state rather than 0 mg/dL.
 */
data class DashboardStats(
    val average: Int,
    val highest: Int,
    val lowest: Int,
    val count: Int,
    val inRangePercent: Int,
    val aboveRangePercent: Int,
    val belowRangePercent: Int,
    // Null when there are no readings of that context in the window - the UI
    // should hide the row, not show 0. A shown 0 mg/dL average would look like
    // a broken meter, and could also mislead a patient into thinking their
    // fasting is fine when in fact it was never measured.
    val fastingAverage: Int?,
    val postMealAverage: Int?
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

    /**
     * Windowed dashboard stats.
     *
     * @param rangeDays 0 = today (from local midnight), otherwise the last N days.
     *
     * Reading-type matching is deliberately fuzzy - people type "Fasting",
     * "fasting", "Before breakfast" and "Nashta se pehle" (Roman Urdu for
     * "before breakfast") to mean the same thing. Missing every alternative
     * spelling is safer than showing a fasting number computed from post-meal
     * data, so unmatched types simply do not contribute to the split averages.
     */
    fun dashboardStats(rangeDays: Int): DashboardStats {
        val now = System.currentTimeMillis()
        val cutoff = if (rangeDays == 0) startOfTodayMillis() else now - rangeDays * 24L * 60 * 60 * 1000

        val recent = _readings.value.filter { it.timestamp >= cutoff }
        if (recent.isEmpty()) {
            return DashboardStats(0, 0, 0, 0, 0, 0, 0, null, null)
        }

        val values = recent.map { toMgdl(it) }
        val avg = values.average().toInt()
        val high = values.max().toInt()
        val low = values.min().toInt()

        val inRange = values.count { it in 70f..180f }
        val below = values.count { it < 70f }
        val above = values.count { it > 180f }

        // Rounded so the three percentages don't sum to 101 in edge cases.
        val n = values.size
        val inRangePct = (inRange * 100) / n
        val belowPct = (below * 100) / n
        val abovePct = 100 - inRangePct - belowPct

        val fasting = averageOfType(recent, ::isFastingType)
        val postMeal = averageOfType(recent, ::isPostMealType)

        return DashboardStats(
            average = avg,
            highest = high,
            lowest = low,
            count = n,
            inRangePercent = inRangePct,
            aboveRangePercent = abovePct,
            belowRangePercent = belowPct,
            fastingAverage = fasting,
            postMealAverage = postMeal
        )
    }

    private fun toMgdl(r: GlucoseReading): Float =
        if (r.unit == "mmol/L") r.value * 18f else r.value

    private fun averageOfType(
        readings: List<GlucoseReading>,
        predicate: (String) -> Boolean
    ): Int? {
        val matched = readings.filter { predicate(it.readingType) }
        if (matched.isEmpty()) return null
        return matched.map { toMgdl(it) }.average().toInt()
    }

    private fun isFastingType(t: String): Boolean {
        val s = t.lowercase().trim()
        return s.contains("fast") ||
                s.contains("before breakfast") ||
                s == "nashta se pehle" ||
                s.contains("naharmunh") ||
                s.contains("nahar munh")
    }

    private fun isPostMealType(t: String): Boolean {
        val s = t.lowercase().trim()
        return s.contains("post") ||
                s.contains("after meal") ||
                s.contains("after food") ||
                s.contains("khane ke baad")
    }

    private fun startOfTodayMillis(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
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
package com.sugarsaathi.app

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The seven reminder kinds. Stored by NAME, not ordinal - reordering this enum
 * must never silently reassign existing rows to a different type.
 */
enum class ReminderType(val emoji: String, val labelRes: Int) {
    GLUCOSE("🩸", R.string.rem_type_glucose),
    MEDICATION("💊", R.string.rem_type_medication),
    WATER("💧", R.string.rem_type_water),
    EXERCISE("🏃", R.string.rem_type_exercise),
    APPOINTMENT("🩺", R.string.rem_type_appointment),
    HBA1C("🧪", R.string.rem_type_hba1c),
    MEAL("🍎", R.string.rem_type_meal);

    companion object {
        fun fromName(value: String): ReminderType =
            entries.firstOrNull { it.name == value } ?: GLUCOSE
    }
}

enum class RepeatMode {
    ONCE,     // a single date and time
    DAILY,    // every day at the given time
    WEEKLY,   // the same weekday every week
    CUSTOM;   // specific weekdays, e.g. Mon/Wed/Fri

    companion object {
        fun fromName(value: String): RepeatMode =
            entries.firstOrNull { it.name == value } ?: ONCE
    }
}

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    /** ReminderType name. Stored as text so the enum can be reordered safely. */
    val type: String = ReminderType.GLUCOSE.name,

    val hour: Int = 8,
    val minute: Int = 0,

    /** RepeatMode name. */
    val repeatMode: String = RepeatMode.DAILY.name,

    /**
     * For ONCE: epoch millis of the chosen date (time comes from hour/minute).
     * Ignored for the repeating modes.
     */
    val dateMillis: Long = 0L,

    /**
     * For WEEKLY and CUSTOM: comma-separated ISO day numbers, Monday = 1 ...
     * Sunday = 7. Example "1,3,5" for Mon/Wed/Fri. Empty for ONCE and DAILY.
     */
    val daysOfWeek: String = "",

    val notes: String = "",

    val enabled: Boolean = true,

    val createdAt: Long = System.currentTimeMillis()
) {
    val typeEnum: ReminderType get() = ReminderType.fromName(type)
    val repeatEnum: RepeatMode get() = RepeatMode.fromName(repeatMode)

    val selectedDays: List<DayOfWeek>
        get() = daysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .map { DayOfWeek.of(it) }

    /**
     * Next time this reminder should fire, or null if it never will again
     * (a ONCE reminder whose moment has passed).
     *
     * @param from the moment to search forward from; injectable for testing.
     */
    fun nextTriggerAt(from: LocalDateTime = LocalDateTime.now()): Long? {
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val zone = ZoneId.systemDefault()

        fun millis(dateTime: LocalDateTime) =
            dateTime.atZone(zone).toInstant().toEpochMilli()

        return when (repeatEnum) {
            RepeatMode.ONCE -> {
                val date = if (dateMillis > 0)
                    java.time.Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
                else LocalDate.now()
                val target = LocalDateTime.of(date, time)
                // A one-off in the past is simply done - do not fire it late.
                if (target.isAfter(from)) millis(target) else null
            }

            RepeatMode.DAILY -> {
                var target = LocalDateTime.of(from.toLocalDate(), time)
                if (!target.isAfter(from)) target = target.plusDays(1)
                millis(target)
            }

            RepeatMode.WEEKLY, RepeatMode.CUSTOM -> {
                val days = selectedDays
                // No days chosen would mean "never", which is almost certainly
                // not what the user meant - fall back to daily.
                if (days.isEmpty()) {
                    var target = LocalDateTime.of(from.toLocalDate(), time)
                    if (!target.isAfter(from)) target = target.plusDays(1)
                    return millis(target)
                }
                // Search the next 8 days so today is considered before wrapping.
                for (offset in 0..7) {
                    val date = from.toLocalDate().plusDays(offset.toLong())
                    if (date.dayOfWeek in days) {
                        val target = LocalDateTime.of(date, time)
                        if (target.isAfter(from)) return millis(target)
                    }
                }
                null
            }
        }
    }
}
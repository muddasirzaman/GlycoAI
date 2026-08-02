package com.sugarsaathi.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glucose_readings")
data class GlucoseReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val value: Float,           // the glucose number
    val unit: String,           // "mg/dL" or "mmol/L"
    val readingType: String,    // "Fasting", "Before Breakfast", etc.

    val timestamp: Long,        // when the reading was taken (epoch millis)

    val note: String = ""       // optional free text or tag
)


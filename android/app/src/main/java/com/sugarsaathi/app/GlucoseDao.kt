package com.sugarsaathi.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseDao {

    @Insert
    suspend fun insert(reading: GlucoseReading)

    @Delete
    suspend fun delete(reading: GlucoseReading)

    // All readings, newest first — powers the History screen
    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC")
    fun getAllReadings(): Flow<List<GlucoseReading>>

    /**
     * Readings after a cutoff time.
     *
     * Currently unused: sevenDaySummary() filters the already-loaded list in
     * memory instead of querying. That is fine at present volumes, but this
     * query is the right approach once a user has months of readings - filtering
     * in SQL beats loading everything and discarding most of it.
     */
    @Suppress("unused")
    @Query("SELECT * FROM glucose_readings WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getReadingsSince(since: Long): Flow<List<GlucoseReading>>
}
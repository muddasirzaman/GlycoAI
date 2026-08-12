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

    /**
     * One-shot snapshot. Used by BackupManager: reading the flow with .first()
     * works but keeps a subscription alive for the whole coroutine, which is
     * wasted work for a single build-a-file operation.
     */
    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<GlucoseReading>

    // Wipes every stored reading. Used by "Delete my data" and by a REPLACE
    // import in BackupManager. There is no soft-delete equivalent here on
    // purpose - if a patient chose Replace, they want the old rows gone.
    @Query("DELETE FROM glucose_readings")
    suspend fun deleteAll()
}
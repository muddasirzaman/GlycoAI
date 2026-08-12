package com.sugarsaathi.app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface Hba1cDao {

    // Newest-first for display. The screen shows most recent at the top and
    // draws the "Current" tile from getLatestOnce() below.
    @Query("SELECT * FROM hba1c_entries ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Hba1cEntry>>

    // One-shot read for the "sync the newest to profile.hba1c" logic. Kept
    // separate from the flow so a manual save can update the profile without
    // subscribing to and cancelling a flow.
    @Query("SELECT * FROM hba1c_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestOnce(): Hba1cEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: Hba1cEntry): Long

    @Delete
    suspend fun delete(entry: Hba1cEntry)
}
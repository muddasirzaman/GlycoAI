package com.sugarsaathi.app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("SELECT * FROM reminders ORDER BY hour ASC, minute ASC")
    fun getAll(): Flow<List<Reminder>>

    /** Used by the worker and the boot receiver, where a Flow is not wanted. */
    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getEnabledOnce(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Reminder?

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
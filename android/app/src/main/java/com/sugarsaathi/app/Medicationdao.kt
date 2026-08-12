package com.sugarsaathi.app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Insert
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)

    @Query("SELECT * FROM medications ORDER BY active DESC, name ASC")
    fun getAll(): Flow<List<Medication>>

    /** For the chat pipeline, which needs a snapshot rather than a stream. */
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name ASC")
    suspend fun getActiveOnce(): List<Medication>

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun count(): Int

    @Query("UPDATE medications SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
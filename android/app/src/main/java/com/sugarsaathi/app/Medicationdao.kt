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

    /**
     * All rows including inactive. Different from getActiveOnce below:
     * a backup export must round-trip an inactive medicine too, or restoring
     * from a file silently loses the fact that the patient once took it.
     */
    @Query("SELECT * FROM medications ORDER BY active DESC, name ASC")
    suspend fun getAllOnce(): List<Medication>

    /** For the chat pipeline, which needs a snapshot of ACTIVE meds. */
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name ASC")
    suspend fun getActiveOnce(): List<Medication>

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun count(): Int

    @Query("UPDATE medications SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    /**
     * Wipes every stored medication. Used only by a REPLACE import in
     * BackupManager - never by the normal medications UI, which uses the
     * per-row delete above.
     */
    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}
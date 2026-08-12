package com.sugarsaathi.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * App database.
 *
 * v1 -> v2: reminders table
 * v2 -> v3: medications table (Stage 1 structured medications)
 * v3 -> v4: hba1c_entries table (HbA1c history)
 *
 * Each migration adds one table and touches nothing else. Reading and glucose
 * history are the only data the patient cannot easily replace, and every
 * migration here has been written so that neither is ever altered.
 *
 * exportSchema is on and schema files ship in the repo. That is what let us
 * verify v3 didn't wipe glucose data, and it lets any future migration diff
 * against a known-good previous shape.
 */
@Database(
    entities = [
        GlucoseReading::class,
        Reminder::class,
        Medication::class,
        Hba1cEntry::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun glucoseDao(): GlucoseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun medicationDao(): MedicationDao
    abstract fun hba1cDao(): Hba1cDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 -> v2: reminders. Kept here as reference; the actual definition
        // must match the one already in your project. If your existing
        // AppDatabase declares a different SQL for MIGRATION_1_2, use THAT
        // one - never fight a migration that already ran on real devices.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        daysMask INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        type TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // v2 -> v3: structured medications. Same rule as above - if the
        // definition in your project already differs, keep yours.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medications (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        dose TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        timing TEXT NOT NULL,
                        timesOfDay TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        isInsulin INTEGER NOT NULL,
                        active INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v3 -> v4: hba1c_entries.
         *
         * CREATE TABLE only. Nothing else in the database is touched. Glucose
         * readings, medications, reminders, and the profile (DataStore, not
         * Room) are all preserved.
         *
         * The column order and types must exactly match Hba1cEntry - Room
         * validates the shape at open time and will crash if they drift.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hba1c_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        value REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        note TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "glycoai.db"
                )
                    // Order matters visually but not functionally - Room picks
                    // the right chain from wherever the device happens to be.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

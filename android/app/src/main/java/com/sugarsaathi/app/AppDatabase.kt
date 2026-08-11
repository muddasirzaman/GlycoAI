package com.sugarsaathi.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * exportSchema is TRUE. Room writes a JSON snapshot of every version to
 * app/schemas/. Those files MUST be committed to git - they are the only
 * record of what each version's tables looked like, and without them a correct
 * migration cannot be written later.
 */
@Database(
    entities = [GlucoseReading::class, Reminder::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun glucoseDao(): GlucoseDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2 adds the reminders table for Smart Reminders.
         *
         * Column types must match exactly what Room expects, or it rejects the
         * migration at runtime with a schema mismatch: TEXT for String,
         * INTEGER for Int/Long/Boolean, REAL for Float/Double. Every NOT NULL
         * column needs a DEFAULT so existing rows remain valid - here the table
         * is new so it is moot, but the habit matters for later migrations.
         *
         * The glucose_readings table is untouched, so existing patient
         * readings survive this upgrade.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `title` TEXT NOT NULL,
                        `type` TEXT NOT NULL DEFAULT 'GLUCOSE',
                        `hour` INTEGER NOT NULL DEFAULT 8,
                        `minute` INTEGER NOT NULL DEFAULT 0,
                        `repeatMode` TEXT NOT NULL DEFAULT 'DAILY',
                        `dateMillis` INTEGER NOT NULL DEFAULT 0,
                        `daysOfWeek` TEXT NOT NULL DEFAULT '',
                        `notes` TEXT NOT NULL DEFAULT '',
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * NOTE the deliberate absence of fallbackToDestructiveMigration(). It
         * is tempting - it makes a crash go away - but it does so by silently
         * deleting the user's glucose history. For someone who has been logging
         * readings for a year and brings them to appointments, that is not
         * recoverable. A crash caught in testing is far better than quiet data
         * loss in the field.
         */
        private val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "glycoai_database"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
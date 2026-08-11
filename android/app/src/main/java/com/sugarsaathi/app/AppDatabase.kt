package com.sugarsaathi.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * exportSchema is now TRUE. Room writes a JSON snapshot of every version to
 * app/schemas/. Those files MUST be committed to git - they are the only
 * record of what each version's tables looked like, and without them a correct
 * migration cannot be written later. You would be guessing.
 */
@Database(
    entities = [GlucoseReading::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun glucoseDao(): GlucoseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Every schema change needs a Migration added here, and the version
         * number above bumped to match.
         *
         * NOTE the deliberate absence of fallbackToDestructiveMigration(). It
         * is tempting - it makes the crash go away - but it does so by silently
         * deleting the user's glucose history. For someone who has been logging
         * readings for a year and brings them to appointments, that is not
         * recoverable. A crash caught in testing is far better than quiet data
         * loss in the field.
         *
         * WORKED EXAMPLE - adding a nullable column in version 2:
         *
         *   1. Add the field to GlucoseReading:
         *        val mealTag: String? = null
         *
         *   2. Bump version above to 2.
         *
         *   3. Add the migration below:
         *
         *        private val MIGRATION_1_2 = object : Migration(1, 2) {
         *            override fun migrate(db: SupportSQLiteDatabase) {
         *                db.execSQL(
         *                    "ALTER TABLE glucose_readings ADD COLUMN mealTag TEXT"
         *                )
         *            }
         *        }
         *
         *   4. Add it to MIGRATIONS.
         *
         * The column type must match what Room expects: TEXT for String,
         * INTEGER for Int/Long/Boolean, REAL for Float/Double. A NOT NULL
         * column also needs a DEFAULT, or the migration fails on existing rows.
         */
        private val MIGRATIONS: Array<Migration> = arrayOf(
            // No migrations yet - version 1 is the first release.
        )

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
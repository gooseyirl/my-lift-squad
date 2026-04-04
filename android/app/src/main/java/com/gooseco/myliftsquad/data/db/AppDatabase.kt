package com.gooseco.myliftsquad.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Squad::class, Athlete::class, CompetitionEntry::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun squadDao(): SquadDao
    abstract fun athleteDao(): AthleteDao
    abstract fun competitionEntryDao(): CompetitionEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `competition_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `athleteSlug` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `meetName` TEXT NOT NULL,
                        `federation` TEXT,
                        `equipment` TEXT,
                        `division` TEXT,
                        `weightClassKg` TEXT,
                        `bodyweightKg` REAL,
                        `best3SquatKg` REAL,
                        `best3BenchKg` REAL,
                        `best3DeadliftKg` REAL,
                        `totalKg` REAL,
                        `place` TEXT,
                        `dots` REAL,
                        `meetCountry` TEXT,
                        `meetTown` TEXT
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_competition_entries_athleteSlug` ON `competition_entries` (`athleteSlug`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE athletes ADD COLUMN isFavourite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "myliftsquad.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.dfrobot.rehab.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrainingSessionEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class RehabDatabase : RoomDatabase() {
    abstract fun trainingSessionDao(): TrainingSessionDao

    companion object {
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(
            Migrations.MIGRATION_1_2,
            Migrations.MIGRATION_2_3,
        )
    }
}

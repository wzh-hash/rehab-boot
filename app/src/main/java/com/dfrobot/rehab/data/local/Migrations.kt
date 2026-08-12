package com.dfrobot.rehab.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

    /**
     * v1 → v2:v1 表结构(avgPressureKg/peakPressureKg)无比例/次数信息,
     * 旧训练记录无法映射,删表重建(v2 entity 结构)。
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS training_sessions")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `training_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `startTimeMillis` INTEGER NOT NULL,
                    `endTimeMillis` INTEGER NOT NULL,
                    `durationMillis` INTEGER NOT NULL,
                    `ratioCode` TEXT NOT NULL,
                    `repsCompleted` INTEGER NOT NULL,
                    `completed` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    /** v2 → v3:新增 steps 列(训练会话内计步)。 */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE training_sessions ADD COLUMN steps INTEGER NOT NULL DEFAULT 0")
        }
    }
}

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
            db.execSQL("DROP TABLE IF EXISTS `training_sessions`")
            db.execSQL(CREATE_SESSIONS_V2)
        }
    }

    /**
     * v2 → v3:新增 steps 列。
     * 用"建新表 + 拷贝 + 删旧表 + 改名"而非 ALTER TABLE ADD COLUMN——
     * Room 期望 steps 列无默认值,而 SQLite 的 ALTER 非空列必须带 DEFAULT,
     * 重建方式可与 Room 导出的 schema 完全一致,规避任何严格校验风险。
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `training_sessions_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startTimeMillis` INTEGER NOT NULL, " +
                "`endTimeMillis` INTEGER NOT NULL, " +
                "`durationMillis` INTEGER NOT NULL, " +
                "`ratioCode` TEXT NOT NULL, " +
                "`repsCompleted` INTEGER NOT NULL, " +
                "`completed` INTEGER NOT NULL, " +
                "`steps` INTEGER NOT NULL)")
            db.execSQL("INSERT INTO `training_sessions_new` " +
                "(`id`, `startTimeMillis`, `endTimeMillis`, `durationMillis`, `ratioCode`, `repsCompleted`, `completed`, `steps`) " +
                "SELECT `id`, `startTimeMillis`, `endTimeMillis`, `durationMillis`, `ratioCode`, `repsCompleted`, `completed`, 0 " +
                "FROM `training_sessions`")
            db.execSQL("DROP TABLE `training_sessions`")
            db.execSQL("ALTER TABLE `training_sessions_new` RENAME TO `training_sessions`")
        }
    }

    /** 与 Room 3.json 导出的 v2 结构逐字一致(steps 由 2→3 重建时加入)。 */
    private const val CREATE_SESSIONS_V2 =
        "CREATE TABLE IF NOT EXISTS `training_sessions` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`startTimeMillis` INTEGER NOT NULL, " +
            "`endTimeMillis` INTEGER NOT NULL, " +
            "`durationMillis` INTEGER NOT NULL, " +
            "`ratioCode` TEXT NOT NULL, " +
            "`repsCompleted` INTEGER NOT NULL, " +
            "`completed` INTEGER NOT NULL)"
}

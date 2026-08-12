package com.dfrobot.rehab.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room 迁移测试:验证 v1/v2 数据库升级到 v3 不崩溃(历史页闪退修复)。
 * 用框架 SQLite 手工造旧库(旧版本 schema 未导出 JSON,无法用 MigrationTestHelper)。
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val v1Ddl = """
        CREATE TABLE IF NOT EXISTS `training_sessions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `startTimeMillis` INTEGER NOT NULL,
            `endTimeMillis` INTEGER NOT NULL,
            `durationMillis` INTEGER NOT NULL,
            `avgPressureKg` REAL NOT NULL,
            `peakPressureKg` REAL NOT NULL
        )
    """.trimIndent()

    private val v2Ddl = """
        CREATE TABLE IF NOT EXISTS `training_sessions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `startTimeMillis` INTEGER NOT NULL,
            `endTimeMillis` INTEGER NOT NULL,
            `durationMillis` INTEGER NOT NULL,
            `ratioCode` TEXT NOT NULL,
            `repsCompleted` INTEGER NOT NULL,
            `completed` INTEGER NOT NULL
        )
    """.trimIndent()

    private fun createOldDb(name: String, version: Int, ddl: String) {
        context.deleteDatabase(name)
        val db = context.openOrCreateDatabase(name, android.content.Context.MODE_PRIVATE, null)
        db.execSQL(ddl)
        db.version = version
        db.close()
    }

    private fun columnsOf(dbName: String): Set<String> {
        val db = context.openOrCreateDatabase(dbName, android.content.Context.MODE_PRIVATE, null)
        val cursor = db.rawQuery("PRAGMA table_info(training_sessions)", null)
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(1))
        }
        cursor.close()
        db.close()
        return columns
    }

    @Test
    fun `v1 升级到 v3 不崩溃且新表结构正确`() = runTest {
        createOldDb("migrate-1", 1, v1Ddl)

        val room = Room.databaseBuilder(context, RehabDatabase::class.java, "migrate-1")
            .addMigrations(*RehabDatabase.MIGRATIONS)
            .build()
        // 触发迁移与查询,不应崩溃
        assertEquals(0, room.trainingSessionDao().observeAll().first().size)
        room.close()

        val columns = columnsOf("migrate-1")
        assertTrue("steps" in columns)
        assertTrue("ratioCode" in columns)
        assertTrue("avgPressureKg" !in columns)
        assertTrue("peakPressureKg" !in columns)
        context.deleteDatabase("migrate-1")
    }

    @Test
    fun `v2 升级到 v3 保留行且 steps 默认 0`() = runTest {
        createOldDb("migrate-2", 2, v2Ddl)
        val seed = context.openOrCreateDatabase("migrate-2", android.content.Context.MODE_PRIVATE, null)
        seed.execSQL(
            """
            INSERT INTO `training_sessions`
                (startTimeMillis, endTimeMillis, durationMillis, ratioCode, repsCompleted, completed)
                VALUES (1000, 2000, 1000, 'A', 3, 1)
            """.trimIndent(),
        )
        seed.close()

        val room = Room.databaseBuilder(context, RehabDatabase::class.java, "migrate-2")
            .addMigrations(*RehabDatabase.MIGRATIONS)
            .build()
        val sessions = room.trainingSessionDao().observeAll().first()
        assertEquals(1, sessions.size)
        assertEquals("A", sessions[0].ratioCode)
        assertEquals(3, sessions[0].repsCompleted)
        assertEquals(0, sessions[0].steps) // 迁移补默认值
        room.close()
        context.deleteDatabase("migrate-2")
    }
}

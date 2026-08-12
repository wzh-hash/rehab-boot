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
 * Room 迁移测试:验证 v1/v2 数据库升级到 v3 不崩溃(历史页闪退修复),
 * 并严格比对迁移后的表结构与 Room 3.json 导出的期望 schema 一致。
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

    /**
     * 期望的 v3 表结构(与 app/schemas/.../3.json 逐字一致):
     * 列名 → (类型, notNull, 默认值)。steps 无默认值。
     */
    private val expectedV3Columns = mapOf(
        "id" to Triple("INTEGER", 1, null),
        "startTimeMillis" to Triple("INTEGER", 1, null),
        "endTimeMillis" to Triple("INTEGER", 1, null),
        "durationMillis" to Triple("INTEGER", 1, null),
        "ratioCode" to Triple("TEXT", 1, null),
        "repsCompleted" to Triple("INTEGER", 1, null),
        "completed" to Triple("INTEGER", 1, null),
        "steps" to Triple("INTEGER", 1, null),
    )

    private fun createOldDb(name: String, version: Int, ddl: String) {
        context.deleteDatabase(name)
        val db = context.openOrCreateDatabase(name, android.content.Context.MODE_PRIVATE, null)
        db.execSQL(ddl)
        db.version = version
        db.close()
    }

    private fun assertSchemaMatchesV3(dbName: String) {
        val db = context.openOrCreateDatabase(dbName, android.content.Context.MODE_PRIVATE, null)
        val cursor = db.rawQuery("PRAGMA table_info(training_sessions)", null)
        val actual = mutableMapOf<String, Triple<String, Int, String?>>()
        while (cursor.moveToNext()) {
            actual[cursor.getString(1)] = Triple(
                cursor.getString(2), // type
                cursor.getInt(3),    // notnull
                cursor.getString(4), // dflt_value
            )
        }
        cursor.close()
        db.close()
        assertEquals("迁移后表结构与 3.json 期望不一致", expectedV3Columns, actual)
    }

    @Test
    fun `v1 升级到 v3 不崩溃且表结构与期望一致`() = runTest {
        createOldDb("migrate-1", 1, v1Ddl)

        val room = Room.databaseBuilder(context, RehabDatabase::class.java, "migrate-1")
            .addMigrations(*RehabDatabase.MIGRATIONS)
            .build()
        // 触发迁移与查询,不应崩溃
        assertEquals(0, room.trainingSessionDao().observeAll().first().size)
        room.close()

        assertSchemaMatchesV3("migrate-1")
        context.deleteDatabase("migrate-1")
    }

    @Test
    fun `v2 升级到 v3 保留行且表结构与期望一致`() = runTest {
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
        assertEquals(0, sessions[0].steps) // 迁移后默认 0
        room.close()

        assertSchemaMatchesV3("migrate-2")
        context.deleteDatabase("migrate-2")
    }

    @Test
    fun `v2 升级到 v3 保留 id 主键与自增`() = runTest {
        createOldDb("migrate-3", 2, v2Ddl)
        val seed = context.openOrCreateDatabase("migrate-3", android.content.Context.MODE_PRIVATE, null)
        seed.execSQL(
            """
            INSERT INTO `training_sessions`
                (startTimeMillis, endTimeMillis, durationMillis, ratioCode, repsCompleted, completed)
                VALUES (1000, 2000, 1000, 'B', 2, 0)
            """.trimIndent(),
        )
        seed.close()

        val room = Room.databaseBuilder(context, RehabDatabase::class.java, "migrate-3")
            .addMigrations(*RehabDatabase.MIGRATIONS)
            .build()
        val dao = room.trainingSessionDao()
        // 迁移后插入新行,验证 AUTOINCREMENT 序号不冲突(旧 id=1 保留)
        val newId = dao.insert(
            TrainingSessionEntity(
                startTimeMillis = 3000, endTimeMillis = 4000, durationMillis = 1000,
                ratioCode = "A", repsCompleted = 3, completed = true,
            ),
        )
        assertTrue(newId > 1)
        assertEquals(2, dao.observeAll().first().size)
        room.close()
        context.deleteDatabase("migrate-3")
    }
}

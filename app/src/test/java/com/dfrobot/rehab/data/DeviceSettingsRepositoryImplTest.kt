package com.dfrobot.rehab.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.dfrobot.rehab.data.local.DeviceSettingsStore
import com.dfrobot.rehab.domain.model.DeviceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DeviceSettingsRepositoryImplTest {

    private lateinit var repo: DeviceSettingsRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // 每个用例独立 DataStore(独立文件 + 独立作用域),避免单例内存缓存跨用例污染
        val file = File(context.filesDir, "settings-test-${System.nanoTime()}.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + Job()),
            produceFile = { file },
        )
        repo = DeviceSettingsRepositoryImpl(DeviceSettingsStore(dataStore))
    }

    @Test
    fun `默认设置为平台默认值`() = runTest {
        val settings = repo.settings.first()
        assertEquals("iot.dfrobot.com.cn", settings.host)
        assertEquals(1883, settings.port)
        assertEquals("", settings.iotId)
    }

    @Test
    fun `保存后流读到最新值`() = runTest {
        val newSettings = DeviceSettings(
            host = "iot.dfrobot.com.cn", port = 1883,
            iotId = "abc", iotPwd = "secret", topic = "BJpHJt1VW",
        )
        repo.saveSettings(newSettings)
        assertEquals(newSettings, repo.settings.first())
    }

    @Test
    fun `不完整配置保存抛异常`() = runTest {
        val error = try {
            repo.saveSettings(DeviceSettings(iotId = "", iotPwd = "", topic = ""))
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue(error != null)
    }

    @Test
    fun `体重百分比保存后流读到`() = runTest {
        repo.saveWeightPercentages(70.0, 25, 50, 75)
        val (weight, percentages) = repo.weightPercentages.first()
        assertEquals(70.0, weight, 0.0)
        assertEquals(Triple(25, 50, 75), percentages)
    }

    @Test
    fun `非法百分比保存抛异常`() = runTest {
        val e1 = try {
            repo.saveWeightPercentages(60.0, 50, 25, 75)
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue(e1 != null)

        val e2 = try {
            repo.saveWeightPercentages(9.0, 25, 50, 75)
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue(e2 != null)
    }
}

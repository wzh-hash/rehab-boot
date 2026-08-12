package com.dfrobot.rehab.ui

import androidx.test.core.app.ApplicationProvider
import com.dfrobot.rehab.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppNavHostTest {

    @Test
    fun `MainActivity 可正常启动并进入 RESUME 状态`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.setup().get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun `应用进程可创建(RehabApplication 初始化 Hilt)`() {
        val app = ApplicationProvider.getApplicationContext<com.dfrobot.rehab.RehabApplication>()
        assertNotNull(app)
    }
}

package com.dfrobot.rehab.testutil

import io.moquette.broker.Server
import java.net.ServerSocket
import java.util.Properties

/**
 * Moquette 嵌入式 MQTT broker 测试工具。
 * 数据目录放 /opt/tmp-exec(沙箱 /tmp 为 noexec,Moquette 无碍但统一安全路径)。
 */
object TestBroker {

    lateinit var server: Server
        private set

    var port: Int = 0
        private set

    fun start() {
        port = ServerSocket(0).use { it.localPort }
        // 每次启动独立数据目录,避免 mapdb 文件锁互相阻塞
        val dataPath = "/opt/tmp-exec/moquette-test-${System.nanoTime()}"
        val props = Properties().apply {
            setProperty("port", port.toString())
            setProperty("host", "127.0.0.1")
            setProperty("data_path", dataPath)
            setProperty("persistent_store", "$dataPath/moquette_store.mapdb")
            setProperty("allow_anonymous", "true")
        }
        server = Server()
        server.startServer(props)
    }

    fun stop() {
        if (::server.isInitialized) {
            server.stopServer()
        }
    }
}

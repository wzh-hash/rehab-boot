# 康复训练助手(智能康复靴)

面向"智控负重脚踝康复训练靴"(掌控板 ESP32)的安卓原生应用。

- 通过 DFRobot Easy IoT 平台(`iot.dfrobot.com.cn:1883`,MQTT)与康复靴双向通信
- 实时监测负重(kg),大数字 + 阈值进度条 + 30s 实时曲线
- 按体重百分比(25%/50%/75%,可调)计算阈值并下发,设备端语音提醒
- 训练会话记录(时长/平均/峰值压力)存本地 Room,供医生评估
- 前台服务常驻连接,断线指数退避自动重连

## 架构

单模块 Clean MVI:`ui/`(纯 Compose)→ `presentation/`(ViewModel + UiState/Intent/Effect)→ `domain/`(模型 + use case + 仓库接口)→ `data/`(HiveMQ MQTT + Room + DataStore)→ `core/`(前台服务 + 单例 MqttConnectionManager)。

技术栈:Kotlin 2.0.20 · Jetpack Compose(Material 3)· Hilt · Room · DataStore · HiveMQ MQTT Client · kotlinx-serialization。

## 文档

- [通信协议(设备固件对接)](docs/PROTOCOL.md)
- [真机验收清单](docs/ACCEPTANCE.md)
- [设计文档](docs/superpowers/specs/2026-08-12-rehab-boot-design.md)

## 构建

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

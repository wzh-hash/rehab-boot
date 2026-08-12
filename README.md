# 康复训练助手(智能康复靴)

面向"智控负重脚踝康复训练靴"(掌控板,Mind+ 固件)的安卓原生应用。

- 通过 DFRobot Easy IoT 平台(`iot.dfrobot.com.cn:1883`,MQTT)与康复靴双向通信
- **远程训练控制台**:一键下发 25/50/75/100% 训练指令(固件短码协议 `A`/`B`/`C`/`D`)
- 实时事件流:设备上线(`hello`)、达到目标重量(`WA`)、完成重复(`plus`)
- 训练会话自动记录(目标比例/次数/时长/完成状态/**步数**)存本地 Room,供医生评估
- 训练会话内计步:手机传感器(TYPE_STEP_COUNTER 优先)自动记录训练步数
- 前台服务常驻连接,断线指数退避自动重连
- 通信协议与掌控板固件完全对齐,详见 [docs/PROTOCOL.md](docs/PROTOCOL.md)
- UI 设计基于医疗冷静·精致化方案(渐变训练按钮/事件时间线/克制动效)

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

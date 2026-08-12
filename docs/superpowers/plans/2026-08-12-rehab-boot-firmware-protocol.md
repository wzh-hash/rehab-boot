# 康复训练助手 v2(对齐 Mind+ 固件)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 v1 应用从 JSON 压力协议改造为掌控板 Mind+ 固件的短码协议:远程训练控制台(25/50/75/100% 指令)+ 事件驱动会话记录。

**Architecture:** 不变(单模块 Clean MVI)。替换 `ProtocolCodec` 为短码编解码;删压力相关领域模型;`SessionTracker` 事件驱动化;监测页改训练控制台;保留 MqttConnectionManager/前台服务/Room/导航。

**Tech Stack:** 同 v1(Kotlin 2.0.20 / Compose BOM 2024.09.02 / Hilt / Room / HiveMQ 1.3.3 / Moquette 测试)。

**Spec:** `docs/superpowers/specs/2026-08-12-rehab-boot-firmware-protocol-design.md`

## 固件协议速查(实现依据)

- 单 topic 双向(固件 topic_1 = `wIOqDXyDg`,app 设置页自填)
- App→设备:`S` 问候 / `A`=25% `B`=50% `C`=75% `D`=100% 训练指令,QoS1,**retained=false**
- 设备→App:`hello`(上线)/ `WA`(达到目标重量)/ `plus`(完成一次重复);其余载荷计无效帧
- 训练循环阻塞 3 次重复;无压力上报;不支持中途停止

## 全局约束

- 保留 v1 已验证基础设施(MQTT 管理/前台服务/Room/DataStore/导航/主题)
- 协议层零容忍乱帧(返回 null 计数)
- 训练中禁用发令按钮;10 分钟无 plus 超时结束(completed=false)
- 每个任务以测试通过 + commit 结束

---

## 任务 V1:领域模型与短码协议层

**Files:**
- Modify: `domain/model/TrainingSession.kt`(字段改:ratio/repsCompleted/completed,删 avg/peak)、`data/protocol/ProtocolCodec.kt`(整体替换为 FirmwareCodec)
- Delete: `domain/model/PressureSample.kt`、`domain/model/Thresholds.kt`、`domain/ThresholdCalculator.kt`
- Create: `domain/model/TrainingRatio.kt`、`domain/model/DeviceEvent.kt`
- Test: `test/…/data/protocol/FirmwareCodecTest.kt`(替换 ProtocolCodecTest)

**Interfaces:**
- Consumes: 无
- Produces:
  - `enum class TrainingRatio(val code: String, val percent: Int) { T25("A",25), T50("B",50), T75("C",75), T100("D",100) }`
  - `sealed interface DeviceEvent { data object Hello; data object RepReached; data object RepCompleted }`
  - `data class TrainingSession(id=0, startTimeMillis, endTimeMillis, durationMillis, ratio: TrainingRatio, repsCompleted: Int, completed: Boolean)`
  - `object FirmwareCodec { fun decodeIncoming(payload: ByteArray): DeviceEvent?; fun encodeCommand(ratio: TrainingRatio): ByteArray; fun encodeHelloTest(): ByteArray }`

- [ ] **Step 1:写失败测试** `FirmwareCodecTest`(`hello`→Hello、`WA`→RepReached、`plus`→RepCompleted、`garbage`→null、空串→null、大小写敏感、encodeCommand(T25)→`A`、encodeHelloTest→`S`、decode 容忍首尾空白 `\n`)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现模型与编解码**(删除 v1 协议文件与 PressureSample/Thresholds/ThresholdCalculator)
- [ ] **Step 4:同步清理引用保持编译绿**:SettingsViewModel 删除 `WeightField`/validate 中的体重百分比段(仅保留连接校验);MonitorViewModel 删除 `observeWeightPercentages` 收集器与 thresholds 字段(状态机完整改造在 V3,此处先删引用);DeviceSettingsRepository/Impl/Store 删除体重百分比 API。对应 VM 测试同步删体重用例(V5 再全量收尾)
- [ ] **Step 5:测试通过**(`testDebugUnitTest` 全绿)
- [ ] **Step 6:提交** `git commit -am "feat: 对齐固件的短码协议与领域模型"`

---

## 任务 V2:SessionTracker 事件驱动化

**Files:**
- Modify: `domain/SessionTracker.kt`(重写)、`test/…/domain/SessionTrackerTest.kt`(重写)

**Interfaces:**
- Consumes: `TrainingRatio`、`TrainingSession`
- Produces:
  - `enum class SessionPhase { Idle, Training }`(v1 的 Paused 删除——固件无暂停)
  - `class SessionTracker(nowMillis: () -> Long = System::currentTimeMillis) { var phase; var repsCompleted: Int; var stats: SessionStats(elapsedMillis, ratio) ; fun start(ratio); fun onRepCompleted(); fun tick(); fun finish(): TrainingSession }`
  - finish:`completed = repsCompleted >= 3`;duration = 冻结 elapsed;Idle 时 finish 抛 IllegalStateException

- [ ] **Step 1:写失败测试**(start(ratio)→phase=Training;onRepCompleted×3→reps=3,finish 后 completed=true、duration=时钟差;中途 finish(1 次)→completed=false;Idle finish 抛异常;tick 推进时长;finish 后回 Idle)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:重写实现**
- [ ] **Step 4:测试通过**
- [ ] **Step 5:提交** `git commit -am "feat: 事件驱动会话引擎"`

---

## 任务 V3:监测页 ViewModel 状态机

**Files:**
- Modify: `presentation/monitor/MonitorContract.kt`、`presentation/monitor/MonitorViewModel.kt`、`test/…/MonitorViewModelTest.kt`(重写)
- Modify: `data/mqtt/MqttTelemetryDataSource.kt`(改事件流)、`core/mqtt/MqttConnectionManager.kt`(publishConfig→publishCommand)
- Modify: `domain/repository/DeviceSettingsRepository.kt`、`data/DeviceSettingsRepositoryImpl.kt`、`data/local/DeviceSettingsStore.kt`(删体重百分比 API)

**Interfaces:**
- Consumes: `FirmwareCodec`、`DeviceEvent`、`TrainingRatio`、`SessionTracker`
- Produces:
  - `data class MonitorUiState(connectionState, deviceOnline: Boolean, phase: SessionPhase, repsCompleted: Int, activeRatio: TrainingRatio?, elapsedMillis: Long, recentEvents: List<EventUi>, invalidFrameCount: Int)` — `EventUi(timeMillis, textRes)` 最近 8 条(时间线)
  - `sealed interface MonitorIntent { StartTraining(ratio); HelloTest; RetryConnect }`
  - `MonitorEffect.ShowMessage`
  - `interface TelemetryDataSource { fun observeEvents(): Flow<DeviceEvent>; suspend fun publishCommand(ratio: TrainingRatio); suspend fun publishHelloTest() }`(删 observeSamples/publishThresholds)
  - `MqttConnectionManager.publishCommand(topic, payload: String, retain: Boolean = false)`(替换 publishConfig;QoS1)
  - 状态机:StartTraining 守卫 `phase==Idle && connectionState==Connected`,否则 ShowMessage;发令后 `tracker.start(ratio)`;RepCompleted → `onRepCompleted` → reps==3 → finish+save+ShowMessage("训练完成,已记录");**超时协程**:进入 Training 时启动 `delay(10min)` 后若仍 Training → finish+save+ShowMessage("训练超时,已记录(未完成)");Hello → deviceOnline=true(断开时复位 false);WA → recentEvents 追加"已达到目标重量";tick 沿用 Dispatchers.Default

- [ ] **Step 1:写失败测试**(fake 事件流:发令 StartTraining(T25)→publishCommand 收到 A、phase=Training;设备发 hello→deviceOnline;WA→事件;plus×3→saveSession 收到 ratio=T25、reps=3、completed=true + ShowMessage;训练中再发 StartTraining 被拒;未连接发令→ShowMessage;断开时 hello 复位;超时:runTest+StandardTestDispatcher+`advanceTimeBy(10min)`→落库 completed=false)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现 VM/Contract/dataSource/manager.publishCommand/仓库删体重 API**
- [ ] **Step 4:测试通过 + assembleDebug 通过**
- [ ] **Step 5:提交** `git commit -am "feat: 训练控制台 ViewModel 与指令下发"`

---

## 任务 V4:监测页 UI 改造

**Files:**
- Modify: `ui/monitor/MonitorScreen.kt`(重写布局)
- Delete: `ui/monitor/PressureChart.kt`、`ui/monitor/ThresholdProgress.kt`
- Modify: `androidTest/…/MonitorScreenTest.kt`(重写)
- Modify: `res/values/strings.xml`(删旧文案,加新文案)

**Interfaces:**
- Consumes: `MonitorUiState`、`MonitorIntent`
- Produces(纯 Compose):
  - `MonitorScreen(state, onIntent, snackbarHostState, modifier)` 布局:连接状态条(点按 RetryConnect)→ 设备状态徽标(已上线/未收到设备消息)→ 四个比例按钮 `2×2`(Idle+Connected 可点;训练中禁用并显示"设备训练中…")→ 问候测试按钮 → 训练进度卡(进行中:"第 X/3 次完成"+ 事件时间线列表(时间+文案);Idle:"选择比例开始训练")→ 会话统计行(时长 mm:ss · 目标 25%)→ 无效帧小字
- 文案:monitor_ratio_25 "25% 训练" 等、monitor_hello_test "语音测试"、monitor_device_online "设备已上线"、monitor_device_unknown "未收到设备消息"、monitor_training_in_progress "设备训练中…"、monitor_rep_progress "第 %1$d/3 次完成"、monitor_event_reached "已达到目标重量"、monitor_event_completed "完成一次重复"、monitor_select_ratio "选择比例开始训练"、session_done "训练完成,已记录"、session_timeout "训练超时,已记录(未完成)"

- [ ] **Step 1:实现新布局 + 预览**(Idle/训练中/完成三态)
- [ ] **Step 2:重写仪器测试**(按钮存在、训练中按钮禁用、点击 25% 发出 StartTraining(T25)、事件时间线渲染)
- [ ] **Step 3:`compileDebugKotlin testDebugUnitTest` 通过**
- [ ] **Step 4:提交** `git commit -am "feat: 远程训练控制台 UI"`

---

## 任务 V5:历史页与设置页改造

**Files:**
- Modify: `presentation/history/HistoryContract.kt`(UiState 不变)、`ui/history/HistoryScreen.kt`(字段:比例/次数/完成状态,删 avg/peak)、`presentation/settings/SettingsContract.kt`、`presentation/settings/SettingsViewModel.kt`(删体重百分比逻辑)、`ui/settings/SettingsScreen.kt`(删训练参数区,加说明文案)
- Modify: `strings.xml`(history_ratio "目标比例 %1$d%%"、history_reps "完成 %1$d/3 次"、history_status_done "已完成"、history_status_incomplete "未完成"、settings_ratio_hint "训练比例在监测页选择(25/50/75/100%),设备端语音播报")
- Test: `test/…/SettingsViewModelTest.kt`(删体重用例,保留连接校验/测试连接用例)

- [ ] **Step 1:改 SettingsViewModel 与测试**(删 WeightField/体重流依赖;校验仅剩 DeviceSettings.isComplete)
- [ ] **Step 2:改 HistoryScreen 与 SettingsScreen 布局**
- [ ] **Step 3:`testDebugUnitTest` 通过**
- [ ] **Step 4:提交** `git commit -am "feat: 历史/设置页适配短码协议"`

---

## 任务 V6:端到端测试与文档更新

**Files:**
- Modify: `test/…/e2e/EndToEndFlowTest.kt`(重写)、`docs/PROTOCOL.md`(重写)、`docs/ACCEPTANCE.md`(重写)、`README.md`(特性描述)、`docs/superpowers/plans/2026-08-12-rehab-boot.md`(注:已废弃,指向 v2)

**Interfaces:**
- 全部组件

- [ ] **Step 1:重写 E2E**:连接 → app `publishCommand(T25)` → 设备端客户端断言收到 `"A"`(且 retained=false)→ 设备端依次 publish `hello`、`WA`、`plus`×3 → app 事件流收到、SessionTracker 完成 → TrainingSessionRepository 收到(ratio=T25,reps=3,completed=true)→ 乱帧 `garbage` → invalidFrameCount 增加
- [ ] **Step 2:全量回归** `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- [ ] **Step 3:重写 PROTOCOL.md**(固件短码协议,含固件源码对照表;注明指令 retained=false 的原因;凭据为固件硬编码的账号级值,换账号需同步改固件)
- [ ] **Step 4:重写 ACCEPTANCE.md**(联调验收:设置页填 iot_id/iot_pwd/topic → 连接 → 点 25% → 设备语音"开始训练" → 踩压达标 → 语音+LED → app 收到 WA/plus → 3 次后会话完成;训练中按钮禁用;设备离线超时场景)
- [ ] **Step 5:提交** `git commit -am "docs: v2 协议与验收文档;test: 短码端到端闭环"`

---

## 验证策略

| 层 | 方式 |
|---|---|
| 短码编解码/会话引擎 | JVM 单测 |
| VM 状态机(含超时) | runTest + StandardTestDispatcher + advanceTimeBy |
| MQTT 收发/端到端 | Moquette 嵌入式 broker |
| UI | 仪器测试 + Preview(真机验收见 ACCEPTANCE.md) |

**沙箱门禁**:每任务 `testDebugUnitTest`(或 assembleDebug)全绿 + commit。

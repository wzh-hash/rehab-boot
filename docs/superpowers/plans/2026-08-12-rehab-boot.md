# 康复训练助手(智能康复靴)Android 应用 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建面向单台"智控负重脚踝康复训练靴"(掌控板 ESP32)的安卓原生应用:通过 DFRobot Easy IoT 平台 MQTT 实时监测负重、下发体重百分比阈值(设备语音提醒)、记录训练会话(时长/平均/峰值压力)供医生评估。

**Architecture:** 单模块 Clean MVI(方案 A,已批准):`ui/`(纯 Compose,状态+回调)→ `presentation/`(ViewModel,UiState/Intent/Effect)→ `domain/`(模型+use case+仓库接口)→ `data/`(HiveMQ MQTT + Room + DataStore)→ `core/`(前台服务保活 + 单例 MqttConnectionManager 拥有连接)。单 Activity + Navigation Compose + Hilt。

**Tech Stack:** Kotlin 2.0.20 · AGP 8.5.2 · Gradle 8.9 · Compose BOM 2024.09.02(Material3)· Hilt 2.51.1 · Room 2.6.1(KSP)· DataStore 1.1.1 · HiveMQ MQTT Client 1.3.3 · kotlinx-serialization-json 1.7.3 · Navigation Compose 2.8.0 · minSdk 26 / compileSdk 34 / targetSdk 34 · JDK 17 · 测试:JUnit4 + Turbine 1.1.0 + kotlinx-coroutines-test + Robolectric 4.13

## 已验证的平台事实(2026-08-12 实地探明,协议设计依据)

| 项 | 事实 |
|---|---|
| Broker | `iot.dfrobot.com.cn`,端口 **1883 仅 TCP**,无 TLS/WebSocket,MQTT 3.1.1 |
| 认证 | MQTT 用户名/密码 = 控制台 `Iot_id(user)` / `Iot_pwd(password)`(账号级,`重新生成` 会作废旧值);ClientID 任意 |
| Topic | **扁平随机字符串即设备 ID**(如 `BJpHJt1VW`),控制台"添加新的设备"生成,**发布与订阅同一 topic**,无层级/通配符 |
| Payload | 透传任意字符串,无格式要求;QoS 0/1/2 与 retained 均支持 |
| 限制 | 免费账号总存储 1 万条、每设备 1000 条(存满即不再存);建议设备端 1~2Hz 上报;无公开 REST API(内部 `api.dfrobot.work` 会话接口不可用) |
| SDK | **无官方 Android SDK**,须用标准 MQTT 3.1.1 客户端 |

来源:官方文档 iot.dfrobot.com.cn/docs、官方 GitHub(DFRobot/Obloq、pxt-DFRobot_WIFI_IoT_UART)、uPyCraft 示例、端口实测探测。

## 产品设计(已批准)

- **单设备应用**,设置页配置:`Iot_id`、`Iot_pwd`、设备 Topic(默认 `iot.dfrobot.com.cn:1883`)
- **通信协议(JSON 信封,单 topic 双向,方向由 type 区分)**:
  - 设备→App(数据):`{"type":"data","p":12.5,"ts":1723456789}`(p=负重 kg,浮点)
  - App→设备(配置):`{"type":"config","p25":15.0,"p50":30.0,"p75":45.0}`,**QoS 1 + retained=true**(设备重连即收到最新阈值)
  - 非法/未知 type 帧一律丢弃并计数(日志)
- **三屏**:监测页(大数字负重 + 三档阈值进度条 + 30s 实时 Canvas 曲线 + 训练会话控件 + 连接状态)/ 历史页(会话列表+详情)/ 设置页(凭据、体重 kg、25/50/75% 百分比)
- **阈值模型**:体重 kg × 百分比 → 阈值 kg 下发;百分比可调
- **训练会话**:空闲→训练中→暂停↔训练中→结束(汇总落库:时长/平均/峰值);会话统计不落时间序列
- **前台服务常驻**(`dataSync` 类型),通知栏显示连接状态;Android 13+ 请求通知权限
- **中文 UI**,医疗康复冷色基调(深青 #0E7490 主色 + 白底 + 灰阶),大字号数字
- **验证**:开发期用 HiveMQ 内置嵌入式 broker(零外部依赖)跑端到端;真机验收清单含真实平台+掌控板

## 全局约束

- 包名 `com.dfrobot.rehab`,应用名"康复训练助手",`minSdk 26`(8.0)以上
- 所有平台连接参数(host/port/iot_id/iot_pwd/topic)必须可在设置页修改,不得硬编码
- MQTT 明文 TCP 1883(平台无 TLS)——设置页注明"连接不含加密,请勿在不可信网络使用"
- 消息频率假设 ≤10Hz;UI 节流 150ms;存储限频 1Hz 落库(会话统计实时,不落时间序列)
- Kotlin 代码遵循 clean-mvi 技能:UiState 不可变、Intent 表达用户意图、Effect 走 Channel 单发、Compose 运行时对象不进 ViewModel、仓库只暴露 suspend/Flow
- 每个任务以可运行/可测试的交付物结束并提交 commit(消息中文+英文动词前缀,如 `feat: 添加阈值计算`)

---

## 文件结构总览

```
/projects/boot/                        ← git 仓库根(任务 1 初始化)
├── settings.gradle.kts · build.gradle.kts · gradle/libs.versions.toml · gradle/wrapper/
├── local.properties(不含 SDK 路径,沙箱用 ANDROID_HOME 环境变量)
├── docs/superpowers/specs/2026-08-12-rehab-boot-design.md   ← 设计文档(任务 1 落库)
├── docs/superpowers/plans/2026-08-12-rehab-boot.md          ← 本计划(任务 1 落库)
├── docs/PROTOCOL.md · docs/ACCEPTANCE.md                    ← 任务 14
└── app/src/
    ├── main/AndroidManifest.xml · res/ · java/com/dfrobot/rehab/
    │   ├── RehabApplication.kt · MainActivity.kt
    │   ├── di/AppModule.kt · DataModule.kt
    │   ├── core/mqtt/MqttConnectionManager.kt
    │   ├── core/service/MqttConnectionService.kt · ConnectionStateNotification.kt
    │   ├── domain/model/{DeviceSettings,Thresholds,PressureSample,TrainingSession,ConnectionState}.kt
    │   ├── domain/ThresholdCalculator.kt · SessionTracker.kt
    │   ├── domain/repository/{DeviceSettingsRepository,TrainingSessionRepository}.kt
    │   ├── data/protocol/ProtocolCodec.kt
    │   ├── data/local/{RehabDatabase,TrainingSessionDao,DeviceSettingsStore}.kt
    │   ├── data/mqtt/MqttTelemetryDataSource.kt
    │   ├── data/…(各 repository 实现)
    │   ├── presentation/monitor/{MonitorViewModel,MonitorContract}.kt
    │   ├── presentation/history/{HistoryViewModel,HistoryContract}.kt
    │   ├── presentation/settings/{SettingsViewModel,SettingsContract}.kt
    │   └── ui/{AppNavHost.kt,theme/} · ui/monitor/ · ui/history/ · ui/settings/
    └── test/…(对应单元测试) · androidTest/(仪器测试,真机跑)
```

---

## 任务 1:环境准备与 Git 仓库初始化

**Files:** 系统层(沙箱)/ `docs/superpowers/specs/2026-08-12-rehab-boot-design.md` / `docs/superpowers/plans/2026-08-12-rehab-boot.md` / `.gitignore` / `README.md`(骨架)

**Interfaces:** 无(基础设施)

- [ ] **Step 1:安装 JDK 17**

```bash
apt-get update && apt-get install -y openjdk-17-jdk-headless
java -version   # 期望 openjdk 17.0.x
```

- [ ] **Step 2:安装 Android cmdline-tools 与 SDK 34**

```bash
mkdir -p /opt/android-sdk/cmdline-tools
curl -sSLo /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/clt.zip -d /opt/android-sdk/cmdline-tools && mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
export ANDROID_HOME=/opt/android-sdk   # 写入 ~/.bashrc
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

验证:`sdkmanager --list_installed` 显示 android-34 与 build-tools;`adb version` 可用。

- [ ] **Step 3:下载 Gradle 8.9 发行版**(后续 wrapper 复用)

```bash
curl -sSLo /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip
unzip -q /tmp/gradle.zip -d /opt && /opt/gradle-8.9/bin/gradle --version   # Gradle 8.9
```

- [ ] **Step 4:git 初始化与首次提交**

```bash
cd /projects/boot && git init -b main
```

`.gitignore`(Android 标准):`.gradle/`、`build/`、`local.properties`、`*.iml`、`.idea/`、`captures/`、`.kotlin/`。

- [ ] **Step 5:落库设计文档与计划**

将本计划内容与"产品设计"部分分别保存为 `docs/superpowers/specs/2026-08-12-rehab-boot-design.md`(设计+协议+平台事实)与 `docs/superpowers/plans/2026-08-12-rehab-boot.md`(本文件),`README.md` 写项目简介。

- [ ] **Step 6:提交**

```bash
git add -A && git commit -m "chore: 初始化仓库与设计文档"
```

---

## 任务 2:Gradle 项目脚手架(可编译骨架)

**Files:**
- Create: `settings.gradle.kts`、`build.gradle.kts`(root)、`gradle/libs.versions.toml`、`gradle/wrapper/`(由 `/opt/gradle-8.9/bin/gradle wrapper --gradle-version 8.9` 生成)、`app/build.gradle.kts`、`app/proguard-rules.pro`、`app/src/main/AndroidManifest.xml`、`RehabApplication.kt`、`MainActivity.kt`(占位)、`res/values/strings.xml`、`res/values/themes.xml`(Compose 用 android:Theme.Material.Light.NoActionBar 父主题)、`res/values/colors.xml`、`res/mipmap-*/ic_launcher`(用 `adaptive_icon` 简单矢量占位)、`ui/theme/{Color,Type,Theme}.kt`(深青 #0E7490 主色、Material3 动态色关闭)

**Interfaces:**
- Consumes: 任务 1 的 JDK17 + ANDROID_HOME + gradle 8.9
- Produces: 可 `assembleDebug` 的骨架;版本目录 `libs.versions.toml` 中所有依赖别名(后续任务引用)

- [ ] **Step 1:写 settings.gradle.kts**

```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "RehabBoot"
include(":app")
```

- [ ] **Step 2:写版本目录 libs.versions.toml**(核心条目,版本见头部 Tech Stack)

```toml
[versions]
agp = "8.5.2"; kotlin = "2.0.20"; ksp = "2.0.20-1.0.25"
composeBom = "2024.09.02"; activityCompose = "1.9.2"; navigationCompose = "2.8.0"
lifecycle = "2.8.6"; hilt = "2.51.1"; hiltNavigationCompose = "1.2.0"
room = "2.6.1"; datastore = "1.1.1"; hivemq = "1.3.3"
serialization = "1.7.3"; coroutines = "1.8.1"; coreKtx = "1.13.1"
junit = "4.13.2"; turbine = "1.1.0"; robolectric = "4.13"; coroutinesTest = "1.8.1"
compileSdk = "34"; targetSdk = "34"; minSdk = "26"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
hivemq-mqtt-client = { group = "com.hivemq", name = "hivemq-mqtt-client", version.ref = "hivemq" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 3:写 root build.gradle.kts 与 app/build.gradle.kts**

root:plugins 别名 apply false。app 模块:apply 全部插件;`compileSdk = 34`、`minSdk = 26`、`targetSdk = 34`、`kotlinOptions { jvmTarget = "17" }`、`composeOptions` 由 compose 插件接管(不写旧 composeOptions)、`buildFeatures { compose = true }`;依赖:compose BOM + material3 + ui + icons-core、activity-compose、navigation-compose、lifecycle(3 个)、hilt + ksp 编译器、room(ksp 编译器)+ room-testing(test)、datastore、hivemq、serialization、coroutines-android;test:junit、turbine、coroutines-test、robolectric;androidTest:compose-ui-test-junit4、compose-ui-test-manifest、room-testing。`testOptions { unitTests { isIncludeAndroidResources = true } }`(Robolectric 需要)。

- [ ] **Step 4:写 AndroidManifest.xml**(本任务仅骨架,服务/权限在任务 7 补全)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application android:name=".RehabApplication" android:label="@string/app_name"
      android:icon="@mipmap/ic_launcher" android:theme="@style/Theme.RehabBoot"
      android:supportsRtl="true">
    <activity android:name=".MainActivity" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
  </application>
</manifest>
```

- [ ] **Step 5:生成 wrapper 并跑通构建**

```bash
cd /projects/boot && /opt/gradle-8.9/bin/gradle wrapper --gradle-version 8.9
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleDebug
```

期望:BUILD SUCCESSFUL,`app/build/outputs/apk/debug/app-debug.apk` 存在。若 `dl.google.com` 下载依赖失败,改用 `mavenCentral` 回退(依赖镜像按需调整,以构建通过为准)。

- [ ] **Step 6:提交** `git add -A && git commit -m "chore: 搭建可编译的 Gradle/Compose/Hilt 骨架"`

---

## 任务 3:领域模型与阈值计算

**Files:**
- Create: `domain/model/DeviceSettings.kt`、`domain/model/Thresholds.kt`、`domain/model/PressureSample.kt`、`domain/model/TrainingSession.kt`、`domain/model/ConnectionState.kt`、`domain/ThresholdCalculator.kt`
- Test: `test/…/domain/ThresholdCalculatorTest.kt`

**Interfaces:**
- Consumes: 无(纯领域)
- Produces:
  - `data class DeviceSettings(host: String = "iot.dfrobot.com.cn", port: Int = 1883, iotId: String, iotPwd: String, topic: String)` — `fun isComplete(): Boolean`(四字段非空/port 1..65535)
  - `data class Thresholds(val p25Kg: Double, val p50Kg: Double, val p75Kg: Double)`
  - `data class PressureSample(val valueKg: Double, val timestampMillis: Long)`
  - `data class TrainingSession(id: Long = 0, startTimeMillis: Long, endTimeMillis: Long, durationMillis: Long, avgPressureKg: Double, peakPressureKg: Double)`
  - `sealed interface ConnectionState { Disconnected; Connecting; Connected }`
  - `object ThresholdCalculator { fun fromPercentages(bodyWeightKg: Double, p25: Int, p50: Int, p75: Int): Thresholds }` — p25/p50/p75 为百分比整数(如 25);约束 `0 < p25 <= p50 <= p75 <= 100`、`bodyWeightKg in 10.0..300.0`,违规抛 `IllegalArgumentException`;换算 `weight * p / 100.0` 保留一位小数

- [ ] **Step 1:写失败测试** `ThresholdCalculatorTest.kt`(用例:60kg×25/50/75 → 15.0/30.0/45.0;非单调百分比抛异常;体重越界抛异常;一位小数舍入)
- [ ] **Step 2:运行确认失败**(`./gradlew :app:testDebugUnitTest` 编译错误)
- [ ] **Step 3:实现领域模型与 ThresholdCalculator**
- [ ] **Step 4:测试通过**
- [ ] **Step 5:提交** `git commit -am "feat: 领域模型与阈值计算"`

---

## 任务 4:通信协议编解码(ProtocolCodec)

**Files:**
- Create: `data/protocol/ProtocolCodec.kt`
- Test: `test/…/data/protocol/ProtocolCodecTest.kt`

**Interfaces:**
- Consumes: `PressureSample`、`Thresholds`
- Produces:
  - `sealed interface MqttMessage { data class Data(val sample: PressureSample): MqttMessage; data class Config(val thresholds: Thresholds): MqttMessage }`
  - `object ProtocolCodec { fun decodeIncoming(payload: ByteArray): MqttMessage? ; fun encodeConfig(thresholds: Thresholds): ByteArray }`
  - decode 规则:UTF-8 JSON → `{"type":"data","p":12.5,"ts":1723456789}` → `MqttMessage.Data`;`{"type":"config","p25":…,"p50":…,"p75":…}` → `Config`;**任何非法 JSON/缺字段/未知 type/数值非法 → 返回 null**(不抛异常,零容忍乱帧);ts 缺失时用注入的 `nowMillis: () -> Long`(默认 `System::currentTimeMillis`),便于测试
  - encodeConfig:`{"type":"config","p25":15.0,"p50":30.0,"p75":45.0}`(kotlinx-serialization `Json { explicitNulls = false }`)

- [ ] **Step 1:写失败测试**(合法 data 帧、合法 config 帧、缺 type、未知 type、非 JSON 文本、p 为字符串、缺 p、ts 回退、encode 输出精确匹配)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现**(kotlinx-serialization,`@Serializable` DTO 私有于文件内)
- [ ] **Step 4:测试通过**
- [ ] **Step 5:提交** `git commit -am "feat: MQTT 协议编解码"`

---

## 任务 5:本地持久化(Room + DataStore)与仓库实现

**Files:**
- Create: `data/local/TrainingSessionDao.kt`、`data/local/RehabDatabase.kt`、`data/local/DeviceSettingsStore.kt`、`data/TrainingSessionRepositoryImpl.kt`、`data/DeviceSettingsRepositoryImpl.kt`
- Test: `test/…/data/local/TrainingSessionDaoTest.kt`、`test/…/data/DeviceSettingsRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `TrainingSession`、`DeviceSettings`、`Thresholds`
- Produces:
  - `@Dao interface TrainingSessionDao { @Insert suspend fun insert(s: TrainingSession): Long; @Query("SELECT * FROM training_sessions ORDER BY startTimeMillis DESC") fun observeAll(): Flow<List<TrainingSession>>; @Query("DELETE FROM training_sessions WHERE id = :id") suspend fun delete(id: Long) }`
  - `@Entity(tableName = "training_sessions") data class TrainingSessionEntity(...)`(字段与 TrainingSession 一致,mapper 在 repository 内)
  - `interface TrainingSessionRepository { fun observeSessions(): Flow<List<TrainingSession>>; suspend fun saveSession(s: TrainingSession); suspend fun deleteSession(id: Long) }`
  - `interface DeviceSettingsRepository { val settings: Flow<DeviceSettings>; suspend fun saveSettings(s: DeviceSettings); val weightPercentages: Flow<Pair<Double, Triple<Int,Int,Int>>>; suspend fun saveWeightPercentages(bodyWeightKg: Double, p25: Int, p50: Int, p75: Int) }`(体重+百分比一并持久化,emit 时经 ThresholdCalculator 校验)
  - `DeviceSettingsStore`:DataStore preferences,键 `host/port/iot_id/iot_pwd/topic/body_weight/p25/p50/p75`,`suspend fun save(...)` 与 `val settingsFlow`、`val trainingFlow`

- [ ] **Step 1:写 DAO 测试**(in-memory Room:`Room.inMemoryDatabaseBuilder(context, RehabDatabase::class.java).allowMainThreadQueries().build()`;insert 后 observeAll 顺序、delete 生效)
- [ ] **Step 2:写 DeviceSettingsRepositoryImpl 测试**(临时 DataStore 文件目录 `context.filesDir`;save→flow 最新值;百分比非法时保存抛 IllegalArgumentException)
- [ ] **Step 3:运行确认失败**
- [ ] **Step 4:实现 DAO/Entity/Database/Store/两个 repository 实现**
- [ ] **Step 5:测试通过**
- [ ] **Step 6:提交** `git commit -am "feat: Room 训练会话存储与 DataStore 设置仓库"`

---

## 任务 6:MQTT 连接管理(MqttConnectionManager)

**Files:**
- Create: `core/mqtt/MqttConnectionManager.kt`、`di/AppModule.kt`(本任务含 manager 的 Hilt 单例绑定)、`data/mqtt/MqttTelemetryDataSource.kt`
- Test: `test/…/core/mqtt/MqttConnectionManagerTest.kt`(HiveMQ 嵌入式 broker)

**Interfaces:**
- Consumes: `DeviceSettings`、`ConnectionState`、`MqttMessage`、`ProtocolCodec`
- Produces(深模块,唯一持 MQTT 客户端处):
  - `class MqttConnectionManager @Inject constructor(@ApplicationContext context: Context)` — 内部持有 `MqttClientAsync`(HiveMQ,`Mqtt3`):
    - `val connectionState: StateFlow<ConnectionState>`
    - `val inboundMessages: SharedFlow<MqttMessage>`(extraBufferCapacity=64,`tryEmit` 丢弃策略,慢消费者不阻塞)
    - `val errorEvents: SharedFlow<String>`(中文用户可读错误:凭据错误/网络不可达/连接被拒绝)
    - `suspend fun connect(settings: DeviceSettings)` — 幂等;ClientID 固定 `"rehab-boot-app"`(平台不校验);cleanSession=true;`automaticReconnect` 关闭(由本类自管);连接失败抛 `MqttConnectionException(message)` 给 UI 层
    - `suspend fun disconnect()` — 幂等,清订阅
    - `suspend fun publishConfig(thresholds: Thresholds)` — QoS 1 + retained=true,发往 settings.topic;未连接时抛异常
    - 内部订阅:连接成功后 `subscribeWith()` 设备 topic,QoS 0;收到消息 → `ProtocolCodec.decodeIncoming` → 非 null 则 `inboundMessages.emit`;无效帧计数 `invalidFrameCount: AtomicInteger`
    - 重连:监听连接断开回调 → `MutableStateFlow` 更新 → 若 `shouldStayConnected`(由本类持有,disconnect() 置 false)则指数退避重连(1s,2s,4s,…上限 60s,`delay` 在 manager 自身 scope);`fun setNetworkRetryFlag()`(供任务 7 网络回调调用)提前重连
  - `class MqttTelemetryDataSource(manager, settingsRepo)` — `fun observeSamples(): Flow<PressureSample>`(inboundMessages filterIsInstance<Data> 映射),`suspend fun publishThresholds(thresholds: Thresholds)`(读最新 settings 后 manager.publishConfig)
- 关键:HiveMQ `MqttClient` 创建用 `MqttClient.builder().identifier("rehab-boot-app").serverHost(settings.host).serverPort(settings.port).buildAsync()`;连接回调监听 `ConnAck` / `onDisconnected` / `onError`(HiveMQ 的 `MqttClientConnectionListener` 与 `addDisconnectedListener`)

- [ ] **Step 1:写失败测试**(嵌入式 broker:`EmbeddedHiveMQService`(com.hivemq.client.mqtt.test 包,随主库提供;若该包不存在,fallback:同一 JVM 起 `HiveMQTestBroker`——以 1.3.3 实际 API 为准,测试代码适配之):
  - connect 后 connectionState=Connected
  - publishConfig 后,同 broker 上订阅同 topic 的客户端收到 retained QoS1 消息且 payload 等于 encodeConfig 输出
  - 外部客户端 publish `{"type":"data","p":9.5,"ts":0}` → manager.inboundMessages 收到 Data(9.5)
  - 外部客户端 publish 乱帧 `garbage` → inbound 无消息且 invalidFrameCount 增加
  - 用错误凭据(broker 需设置真实密码验证,嵌入式 broker 无认证 → 此用例改测:**broker 未启动时 connect 抛 MqttConnectionException**,以及**disconnect 后不再自动重连**)
  - 自动重连:连接成功后 `manager.internalKillConnectionForTest()`(测试钩子,生产不用)或直接停 broker → connectionState 转 Disconnected → 重启 broker → 自动回 Connected
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现 manager + dataSource + AppModule 单例绑定**(`@Provides @Singleton fun provideMqttConnectionManager(context): MqttConnectionManager`)
- [ ] **Step 4:测试通过**(Turbine 收集 StateFlow/SharedFlow)
- [ ] **Step 5:提交** `git commit -am "feat: MQTT 连接管理与遥测数据源"`

---

## 任务 7:前台服务与通知

**Files:**
- Create: `core/service/MqttConnectionService.kt`、`core/service/ConnectionStateNotification.kt`、`core/service/NetworkMonitor.kt`
- Modify: `AndroidManifest.xml`、`MainActivity.kt`(通知权限请求)

**Interfaces:**
- Consumes: `MqttConnectionManager`、`DeviceSettingsRepository`
- Produces:
  - `class MqttConnectionService : Service()` — `onCreate` 注入 manager + settingsRepo(需 `@AndroidEntryPoint` 或手动 `ServiceLocator`;用 Hilt `@AndroidEntryPoint` + `@Inject lateinit var manager`):读取最新 settings 后 `manager.connect(...)`;`onStartCommand` 返回 `START_STICKY`;`onDestroy` → `manager.disconnect()`;`onTaskRemoved` 不主动停(常驻,用户从设置页"断开"才停)
  - `object ConnectionStateNotification { fun build(context, state: ConnectionState): Notification }` — channel `rehab_connection`(IMPORTANCE_LOW),title "康复训练助手",text 按状态:"正在连接…"/"已连接 · 设备 Topic:xxx"/"已断开,等待重连…";tap → MainActivity
  - `class NetworkMonitor(context)` — `ConnectivityManager.registerDefaultNetworkCallback`,网络恢复时调 `manager.setNetworkRetryFlag()`(不持有长生命周期引用,回调内软引用)
  - 启动/停止:设置页"启用连接"开关 → `ContextCompat.startForegroundService` / `context.stopService`;`onCreate` 里 `startForeground(ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)`(Android 14 合规)
- Manifest:`<uses-permission android:name="android.permission.INTERNET"/>`、`<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>`、`<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`、`<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>`、`<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>`;service 声明 `android:foregroundServiceType="dataSync"`、`android:exported="false"`
- 通知权限:MainActivity 在 Android 13+ 用 `rememberLauncherForActivityResult(RequestPermission)` 于首启请求;拒绝后设置页显示引导文案

- [ ] **Step 1:写 ConnectionStateNotification 单测**(状态→标题/文本映射;Robolectric 构造 Notification,断言 channel id 与内容)
- [ ] **Step 2:实现 service/notification/networkMonitor + manifest + 权限请求**(服务启动逻辑在任务 12 设置页接线)
- [ ] **Step 3:单测通过 + `./gradlew :app:assembleDebug` 通过**
- [ ] **Step 4:提交** `git commit -am "feat: 前台服务保活与连接状态通知"`

---

## 任务 8:训练会话引擎(SessionTracker)

**Files:**
- Create: `domain/SessionTracker.kt`
- Test: `test/…/domain/SessionTrackerTest.kt`

**Interfaces:**
- Consumes: `PressureSample`、`TrainingSession`
- Produces(纯类,无协程无 Android):
  - `enum class SessionPhase { Idle, Running, Paused }`
  - `data class SessionStats(val elapsedMillis: Long, val sampleCount: Int, val avgPressureKg: Double, val peakPressureKg: Double)`
  - `class SessionTracker { fun start(); fun pause(); fun resume(); fun ingest(sample: PressureSample); fun finish(): TrainingSession; val phase: SessionPhase; val stats: SessionStats }`
  - 语义:start 记 `baseStart`;pause 冻结 elapsed;resume 平移 baseStart;ingest 仅在 Running 时计入(采样≤10Hz,直接累加不降频);avg = 累计和/计数;peak 取 max;finish 校验 phase != Idle(Idle 抛 IllegalStateException),生成 endTimeMillis = 当前注入时钟、durationMillis = 冻结 elapsed、avg/peak 归零时以 0.0 落库;注入 `nowMillis: () -> Long`

- [ ] **Step 1:写失败测试**(完整会话:start→ingest×5(含峰值)→pause(冻结)→resume→ingest→finish 的 avg/peak/时长精确断言;Paused 时 ingest 不计;Idle 时 finish 抛异常;空会话 finish 得 0.0)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现**
- [ ] **Step 4:测试通过**
- [ ] **Step 5:提交** `git commit -am "feat: 训练会话状态机与统计"`

---

## 任务 9:监测页 ViewModel

**Files:**
- Create: `presentation/monitor/MonitorContract.kt`、`presentation/monitor/MonitorViewModel.kt`、`presentation/monitor/ThresholdUiState.kt`(若与 Contract 合并则省略)
- Test: `test/…/presentation/monitor/MonitorViewModelTest.kt`

**Interfaces:**
- Consumes: `MqttTelemetryDataSource`、`DeviceSettingsRepository`、`TrainingSessionRepository`、`SessionTracker`、`ThresholdCalculator`、`ConnectionState`
- Produces:
  - `data class MonitorUiState(val connectionState: ConnectionState, val livePressureKg: Double?, val livePressureAtMillis: Long?, val thresholds: Thresholds?, val thresholdPercentages: Triple<Int,Int,Int>, val bodyWeightKg: Double, val phase: SessionPhase, val stats: SessionStats, val invalidFrameCount: Int, val isConnecting: Boolean)`
  - `sealed interface MonitorIntent { StartSession; PauseSession; ResumeSession; FinishSession; RetryConnect; ShowThresholds(percentages) }`(`ShowThresholds` 由设置页跳回后带参:体重+百分比 → 重新换算并 publish)
  - `sealed interface MonitorEffect { ShowMessage(message: String) }`
  - 装配:构造时收集 `dataSource.observeSamples()`(150ms 节流 UI 压力;SessionTracker.ingest 原始流)、`manager.connectionState`、`settingsRepo.settings`(weight/percentages 变化 → 重新算 thresholds → 自动 publishConfig)、`settingsRepo.weightPercentages`;`StartSession` → tracker.start;`FinishSession` → tracker.finish → `saveSession` → Effect.ShowMessage("训练完成,已保存");`RetryConnect` → 读 settings → manager.connect(异常 → ShowMessage(异常中文文案));`isConnecting` 防抖(connecting 期间 RetryConnect 忽略)
  - 测试要点:VM 不持 Compose 对象;所有依赖可 fake(接口而非实现)

- [ ] **Step 1:写失败测试**(fake dataSource 发样本→livePressureKg 更新;StartSession→phase=Running;ingest→stats 变化;FinishSession→repository.saveSession 收到 TrainingSession + Effect;设置变化→publishConfig 收到新阈值;RetryConnect 失败→ShowMessage;connecting 期间忽略重复 Retry)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现 Contract + ViewModel**(依赖注入:VM 构造器参数全接口;`@HiltViewModel` + `@Inject constructor`;Channel(BUFFERED) effect)
- [ ] **Step 4:测试通过**
- [ ] **Step 5:提交** `git commit -am "feat: 监测页 ViewModel"`

---

## 任务 10:监测页 UI

**Files:**
- Create: `ui/monitor/MonitorScreen.kt`(含 `MonitorRoute`)、`ui/monitor/PressureChart.kt`(Canvas 折线)、`ui/monitor/ThresholdProgress.kt`、`ui/monitor/components.kt`(大数字、会话控件)
- Modify: `ui/theme/`
- Test: `androidTest/…/MonitorScreenTest.kt`(仪器测试,真机/模拟器跑)

**Interfaces:**
- Consumes: `MonitorUiState`、`MonitorIntent`、`MonitorEffect`
- Produces(全部纯 Compose,状态+回调):
  - `@Composable fun MonitorRoute(viewModel: MonitorViewModel = hiltViewModel(), modifier: Modifier = Modifier)` — 收集 state + effects;effect → SnackbarHost
  - `@Composable fun MonitorScreen(state: MonitorUiState, onIntent: (MonitorIntent) -> Unit, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier)` — 布局:顶部连接状态条(点按 → RetryConnect;断开时黄色提示"已断开,点按重连")→ 大数字区(`displayLarge` 字体,`%.1f kg`,无数据时显示"-- kg"与"等待设备数据…")→ ThresholdProgress(三段进度条,25/50/75 标记线与当前值指针,超阈值段落变色)→ PressureChart(30s 滚动窗口,内部 `remember` 环形缓冲 `ArrayDeque<Float>` 由 livePressure 驱动,Canvas 描线+网格,无依赖)→ 会话控件(Running:暂停/结束按钮;Paused:继续/结束;Idle:开始)→ 底部"数据帧无效:N"小字(调试)
  - 文案全走 `strings.xml`(中文)
- 节流细节:VM 已节流,UI 不再节流;chart 环形缓冲在 composition 内(UI 本地状态,不进 VM——符合 clean-mvi 边界)

- [ ] **Step 1:实现 PressureChart + ThresholdProgress 私有预览**(`@Preview` 各状态:无数据/训练中/暂停/断开)
- [ ] **Step 2:实现 MonitorScreen + MonitorRoute 装配**
- [ ] **Step 3:写仪器测试**(`createAndroidComposeRule<MainActivity>()`,注入 fake state 断言:数字显示、会话按钮文本随 phase 切换、点击结束触发 onIntent 捕获——本任务先写测试,真机执行在验收阶段)
- [ ] **Step 4:`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` 通过**
- [ ] **Step 5:提交** `git commit -am "feat: 监测页 UI(大数字/阈值进度/实时曲线/会话控件)"`

---

## 任务 11:历史页

**Files:**
- Create: `presentation/history/HistoryContract.kt`、`presentation/history/HistoryViewModel.kt`、`ui/history/HistoryScreen.kt`
- Test: `test/…/presentation/history/HistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `TrainingSessionRepository`
- Produces:
  - `data class HistoryUiState(val sessions: ImmutableList<TrainingSession>, val isLoading: Boolean)`
  - `sealed interface HistoryIntent { DeleteSession(id: Long) }`(长按/滑动删除,确认对话框在 UI 层,确认后发 Intent)
  - `HistoryViewModel`:`observeSessions().map{…}.stateIn(WhileSubscribed(5000))`;删除成功 → `Effect.ShowMessage("已删除")`;空列表 UI 显示"暂无训练记录,去监测页开始第一次训练吧"

- [ ] **Step 1:写失败测试**(fake repo:初始空→sessions 空;insert 后 UI 更新;DeleteSession → repo.delete 被调)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现 Contract/VM/Screen**(列表项:日期(格式化 `yyyy-MM-dd HH:mm`)、时长(分:秒)、平均/峰值 kg;点击 → AlertDialog 详情;滑动删除 + 确认)
- [ ] **Step 4:测试通过**
- [ ] **Step 5:提交** `git commit -am "feat: 历史页与删除"`

---

## 任务 12:设置页

**Files:**
- Create: `presentation/settings/SettingsContract.kt`、`presentation/settings/SettingsViewModel.kt`、`ui/settings/SettingsScreen.kt`
- Test: `test/…/presentation/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `DeviceSettingsRepository`、`MqttConnectionManager`、`SessionTracker`(取 phase 显示"训练进行中不可保存"提示)
- Produces:
  - `data class SettingsUiState(val settings: DeviceSettings, val bodyWeightKg: String, val p25: String, val p50: String, val p75: String, val connectionEnabled: Boolean, val isSaving: Boolean, val validationError: String?)`
  - `sealed interface SettingsIntent { FieldChanged(field, value); Save; ToggleConnection; TestConnection; Back }`
  - 保存校验(本地):`DeviceSettings.isComplete()`、体重 10..300、`0 < p25 <= p50 <= p75 <= 100`(整数解析);失败 → `validationError` 中文文案;成功 → saveSettings/saveWeightPercentages → **ToggleConnection 联动**:若此前连接启用则重启服务(stop + start,任务 7 的 service);未启用则仅保存
  - `TestConnection`:`manager.connect(settings)` → 立即 `disconnect()`;成功 → ShowMessage("连接成功");失败 → ShowMessage(错误中文)
  - 明文提示:UI 底部固定小字"⚠ 平台连接不含加密(明文 TCP 1883),请勿在不可信网络使用"

- [ ] **Step 1:写失败测试**(空 iotId 保存→validationError;体重越界;百分比非单调;合法保存→repo 收到值;TestConnection 成功/失败路径;连接已启用时保存触发重启标记)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现 Contract/VM/Screen**(三段:平台连接(host/port/iot_id/iot_pwd/topic,密码框 `PasswordVisualTransformation`、"测试连接"按钮、启用连接开关)/ 训练参数(体重 kg、25%/50%/75% 数值框)/ 关于(版本号))
- [ ] **Step 4:测试通过 + assembleDebug 通过**
- [ ] **Step 5:提交** `git commit -am "feat: 设置页与连接开关"`

---

## 任务 13:导航与整体装配

**Files:**
- Create: `ui/AppNavHost.kt`、`MainActivity.kt`(重写为 Scaffold + NavigationBar 三目的地:监测/历史/设置;`navController` 与 back stack 状态保持)
- Modify: `ui/theme/Theme.kt`(补 NavigationBar 配色)、`RehabApplication.kt`(空,`@HiltAndroidApp`)
- Test: `test/…/ui/AppNavHostTest.kt`(Robolectric:启动 MainActivity,断言三目的地存在、切换可点击;日志无崩溃)

**Interfaces:**
- Consumes: 三个 Route 组合件
- Produces: `@Composable fun AppNavHost(navController: NavHostController)` — routes:`"monitor"`、`"history"`、`"settings"`;底部 NavigationBar 三个 `NavigationBarItem`(图标:monitor→Icons.Outlined.FavoriteBorder/历史→DateRange/设置→Settings,文字中文);`navController.navigate(route){ popUpTo(route){saveState=true}; launchSingleTop=true; restoreState=true }` 标准底部导航模式;监测页置顶时刷新阈值参数(任务 9 的 ShowThresholds 由 settings 页保存后经共享 state 自动触发,无需传参)

- [ ] **Step 1:写 Robolectric 冒烟测试**(`@RunWith(RobolectricTestRunner::class)`,`@Config(sdk = [34])`,`RuntimeEnvironment.getApplication()`;用 `createAndroidComposeRule` 或直接 `ComposeTestRule` 包 `AppNavHost`——以 Robolectric 4.13 + compose ui-test 实际兼容性为准,若 Compose-on-Robolectric 不可行则退化为:MainActivity 启动无崩溃 + 三 route 单测各自组合可用)
- [ ] **Step 2:实现 AppNavHost + MainActivity 装配 + 主题配色**
- [ ] **Step 3:测试通过 + `./gradlew :app:assembleDebug` 通过**
- [ ] **Step 4:提交** `git commit -am "feat: 底部导航与整体装配"`

---

## 任务 14:端到端验证与交付文档

**Files:**
- Create: `test/…/e2e/EndToEndFlowTest.kt`、`docs/PROTOCOL.md`、`docs/ACCEPTANCE.md`、`README.md`(完善)
- Test: `EndToEndFlowTest.kt`

**Interfaces:**
- Consumes: 全部组件
- Produces: 端到端测试 + 协议文档 + 验收清单

- [ ] **Step 1:写端到端测试**(嵌入式 broker 模拟完整闭环:以真实 `MqttConnectionManager` + `ProtocolCodec` + 假 settingsRepo 组装 → 启动连接 → 模拟设备端客户端 publish data 帧 ×20(2Hz)→ 断言 inbound 收到、SessionTracker 走完会话、TrainingSessionRepository 收到汇总 → 调 publishConfig → 断言设备端客户端收到 retained config 帧且 decode 正确)
- [ ] **Step 2:运行通过;再全量回归** `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- [ ] **Step 3:写 docs/PROTOCOL.md**(给掌控板固件的对接文档:broker/认证/单 topic JSON 信封/两帧格式/频率建议(1~2Hz,注意每设备 1000 条存储上限)/retained config 语义/示例代码片段(MicroPython 或 Arduino MQTT 均可,给出与平台官方示例一致的连接参数))
- [ ] **Step 4:写 docs/ACCEPTANCE.md**(真机验收清单:① 平台注册与创建设备获取 Iot_id/Iot_pwd/Topic ② 设置页填写并测试连接 ③ 掌控板固件按 PROTOCOL.md 实现后联调 ④ 实时数字/曲线/阈值进度 ⑤ 会话记录与历史 ⑥ 断网重连(飞行模式切换)⑦ 息屏后台收数据(前台服务)⑧ 通知权限拒绝场景)
- [ ] **Step 5:最终提交** `git add -A && git commit -m "docs: 协议文档与验收清单;test: 端到端闭环"`

---

## 验证策略总览

| 层 | 方式 | 位置 |
|---|---|---|
| 阈值计算/会话引擎/协议编解码 | JVM 单测 | `testDebugUnitTest` |
| MQTT 连接(重连/收发) | HiveMQ 嵌入式 broker 单测 | `testDebugUnitTest` |
| ViewModel | fake 仓库单测 + Turbine | `testDebugUnitTest` |
| 前台服务通知 | Robolectric | `testDebugUnitTest` |
| 端到端闭环 | 嵌入式 broker 全链路 | `testDebugUnitTest` |
| UI 渲染/交互 | Compose 仪器测试(真机)+ `@Preview` 自检 | `androidTest` / 真机 |
| 真实平台+掌控板 | 用户按 ACCEPTANCE.md 验收 | 用户环境 |

**沙箱侧门禁**:每个任务结束 `./gradlew :app:testDebugUnitTest`(或 assembleDebug)全绿 + 对应 commit;任务 14 后全量回归。

**已知环境限制**:沙箱无模拟器,仪器测试与真机联调由用户执行;UI 正确性靠 Preview/Review 与仪器测试代码兜底。

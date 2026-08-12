# 康复训练助手 v3 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复历史页闪退(Room 迁移)、加入训练会话内计步(手机传感器)、按 Hermes 设计提案全面美化前端。

**Architecture:** 不变。新增 `core/sensor/StepSensor` 深模块;Room version 3 + 迁移链;`TrainingSession.steps`;UI 按 Hermes 提案 P0+P1+P2 重构。

**Spec:** `docs/superpowers/specs/2026-08-12-rehab-boot-v3-design.md`(含 Hermes 提案全文摘要)

## 全局约束

- 会话计步:训练开始基线 → 结束差值;`SessionTracker` 注入 `stepProvider: () -> Int`(测试假数据)
- 权限:ACTIVITY_RECOGNITION manifest + Android 10+ 运行时请求;拒绝不影响训练
- 迁移链:MIGRATION_1_2(删表重建)+ MIGRATION_2_3(ALTER TABLE steps)
- UI 实现须保持架构边界(纯 Compose + 状态/回调,ViewModel 不入 UI)
- 每任务测试通过 + commit

---

## 任务 v3-1:Room 迁移(修复历史页闪退)

**Files:**
- Modify: `data/local/RehabDatabase.kt`(version=3 + migrations)、`app/build.gradle.kts`(exportSchema=true + schemaLocation 供 MigrationTestHelper)
- Create: `data/local/Migrations.kt`、`test/…/data/local/MigrationTest.kt`

**Interfaces:**
- 迁移 2→3:`ALTER TABLE training_sessions ADD COLUMN steps INTEGER NOT NULL DEFAULT 0`
- 迁移 1→2:旧表(avgPressureKg/peakPressureKg)无映射价值,DROP TABLE + CREATE(与 v2 entity 一致)

- [ ] **Step 1:写 MigrationTest**(`MigrationTestHelper` + `Room.databaseBuilder(...).addMigrations(...)`):v1 schema 建表 → migrate 到 3 → 可查询;v2 schema 插入一行 → migrate → steps 默认 0 且行保留)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现**(schemaLocation 导出 JSON;Migrations.kt)
- [ ] **Step 4:测试通过 + commit** `feat: Room 迁移修复历史页闪退`

---

## 任务 v3-2:传感器计步(StepSensor + 会话集成)

**Files:**
- Create: `core/sensor/StepSensor.kt`(深模块:传感器选择/注册/基线/差值/能力)
- Modify: `domain/model/TrainingSession.kt`(+steps: Int = 0)、`domain/SessionTracker.kt`(+stepProvider 注入,start 记基线、finish 算差值)、`data/local/TrainingSessionDao.kt`(+steps 列)、`data/local/Migrations.kt`(2→3 已有)、`presentation/monitor/MonitorContract.kt`(+stepsSupported/steps 到 UiState)、`presentation/monitor/MonitorViewModel.kt`(训练中持续采样 stepProvider 进 state;finish 时 steps 进会话)
- Modify: `AndroidManifest.xml`(ACTIVITY_RECOGNITION + uses-feature)
- Test: `test/…/core/sensor/StepSensorTest.kt`(假 SensorManager 逻辑:基线/差值/不可用)、`test/…/domain/SessionTrackerTest.kt`(+计步用例)、`test/…/presentation/monitor/MonitorViewModelTest.kt`(+steps 进会话)

**Interfaces:**
- `class StepSensor(context, onStepsChanged: (Int) -> Unit)` — `val isSupported: Boolean`;`fun start()`/`fun stop()`;内部持有 SensorManager + SensorEventListener,STEP_COUNTER 优先、STEP_DETECTOR 备选;`currentSteps: Int` 由回调累加/差值维护
- `SessionTracker(nowMillis, stepProvider: () -> Int)`:`start` 记 `baseSteps = stepProvider()`;`finish` → `steps = (stepProvider() - baseSteps).coerceAtLeast(0)`
- `MonitorUiState.steps: Int`(训练中实时)与 `stepsSupported: Boolean`

- [ ] **Step 1:写失败测试**(假 stepProvider:start 后 +10 → finish 步数=10;finish 前 provider 不动 → 0;VM:StartTraining 后 steps 更新进 state、finish 后会话含 steps)
- [ ] **Step 2:运行确认失败**
- [ ] **Step 3:实现**(StepSensor 用 `SensorManager.getDefaultSensor`;registerListener 仅 Training 期间;VM 用注入的 StepSensor 工厂——测试注入 fake)
- [ ] **Step 4:测试通过 + assembleDebug + commit** `feat: 训练会话内计步(手机传感器)`

---

## 任务 v3-3:UI 美化实现(委托 Hermes)

**Files:**(Hermes 按设计提案实现)
- Modify: `ui/theme/{Color,Type,Theme}.kt`(token 表/排版/渐变)、`ui/monitor/MonitorScreen.kt`(重构:合并状态栏/渐变按钮/进度卡/时间线)、`ui/history/HistoryScreen.kt`(卡片/AssistChip/步数)、`ui/settings/SettingsScreen.kt`(分组卡片化 + Topic 说明文案 + 明文警告换图标)、`res/values/strings.xml`(全量文案)、`ui/AppNavHost.kt`(三页 TopAppBar,若提案含)、`androidTest/…/MonitorScreenTest.kt`(文案断言同步)

**Interfaces:**
- Consumes: `MonitorUiState`(含 steps/stepsSupported)、`MonitorIntent`、各契约不变
- 实现细节以 `docs/superpowers/specs/2026-08-12-rehab-boot-v3-design.md` §4 与 Hermes 提案为准;动效 3 处;P3 不做

- [ ] **Step 1:委托 Hermes**(delegate_to_agent,working_dir=/projects/boot,附设计文档路径与最终接口说明)
- [ ] **Step 2:审查 Hermes 输出**(编译 + 测试 + 与设计提案对照)
- [ ] **Step 3:commit**(Hermes 输出验收后)`feat: 前端美化(设计提案 P0-P2)`

---

## 任务 v3-4:收尾(权限请求 + 回归 + 文档)

**Files:**
- Modify: `ui/monitor/MonitorScreen.kt`(训练开始前请求 ACTIVITY_RECOGNITION 权限——launcher 在 Route)、`docs/ACCEPTANCE.md`(+计步/升级验证项)、`docs/PROTOCOL.md`(无协议变更,仅确认)、README(特性)
- Test: 全量回归 `testDebugUnitTest` + `assembleDebug`

- [ ] **Step 1:权限请求接线**(MonitorRoute 训练意图前检查/请求;拒绝 → steps 显示不可用)
- [ ] **Step 2:全量回归**
- [ ] **Step 3:文档更新 + commit** `docs: v3 验收与说明;test: 回归`

---

## 验证策略

| 层 | 方式 |
|---|---|
| Room 迁移 | MigrationTestHelper(MigrationTest) |
| StepSensor | 假 SensorManager 单测 |
| 会话计步 | 假 stepProvider 单测 |
| VM 步数流转 | fake 单测 |
| UI | 编译 + 仪器测试文案断言 + 真机验收(ACCEPTANCE.md) |
| 闪退修复 | 真机从 v1/v2 升级路径验证(用户执行) |

**沙箱门禁**:每任务 testDebugUnitTest/assembleDebug 全绿 + commit。

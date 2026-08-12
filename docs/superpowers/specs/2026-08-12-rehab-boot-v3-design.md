# 康复训练助手 v3 — 闪退修复 · 传感器计步 · 前端美化

日期:2026-08-12 · 状态:已批准

## 背景

用户真机反馈:① 历史页闪退;② 设置页 topic 只填一个但固件数组有两个;③ 首页视觉不佳;④ 希望加入手机传感器计步。UI 设计提案由 Hermes Agent 产出(2026-08-12,全文见下方摘要),架构与数据方案由主会话设计。

## 1. 历史页闪退修复(根因已确认)

- **根因**:v1 数据库表(avgPressureKg/peakPressureKg 字段)与 v2 entity(ratioCode/repsCompleted/completed)不匹配,`RehabDatabase version=1` 未升级,无 Migration → 历史页触发查询时 `IllegalStateException: Room cannot verify the data integrity` → 闪退
- **修复**:`version = 3`;
  - `MIGRATION_1_2`:旧表行无比例/次数信息,删表重建(DROP + CREATE)
  - `MIGRATION_2_3`:`ALTER TABLE training_sessions ADD COLUMN steps INTEGER NOT NULL DEFAULT 0`
  - 单测:Room `MigrationTestHelper` 验证 2→3(1→2 为空表重建)

## 2. 训练会话内计步(手机传感器)

- **传感器**:`TYPE_STEP_COUNTER` 优先(硬件累计步数,取差值);`TYPE_STEP_DETECTOR` 备选(逐事件累加);均无 → UI 显示"设备不支持计步"
- **权限**:manifest 声明 `ACTIVITY_RECOGNITION` + `<uses-feature android:name="android.hardware.sensor.stepcounter" android:required="false"/>`;Android 10+ 训练开始时运行时请求;拒绝 → 仅计步不可用,训练不受影响
- **数据流**:`StepSensor`(封装传感器生命周期)→ 训练开始记录基线 → 训练中持续监听(前台服务保活保证进程存活)→ 结束取差值 → `TrainingSession.steps`
- **模型**:`TrainingSession` 加 `steps: Int` 字段;SessionTracker 经注入的 `stepProvider: () -> Int` 计算(测试可注入假数据)
- **UI**:监测页进度卡显示"步数 X";历史页卡片与详情显示步数

## 3. 双 topic 说明

固件收发均用 `wIOqDXyDg`(`q4F3DXyDg` 未被固件代码引用)。设置页 Topic 字段下加说明:"发布与订阅使用同一 Topic(与康复靴固件约定)"。

## 4. 前端美化(Hermes 提案 P0+P1+P2)

### 配色(核心改动)
- `surface = #FFFFFF`(Light,与 background #F8FAFC 拉开,卡片有层次)/ Dark `#111A2C`
- 新增状态色:success #16A34A、warning #D97706(含 dark 变体)
- **训练按钮渐变**(135°):25% `#A5F3FC→#67E8F9`(文字 #164E63)、50% `#22D3EE→#0891B2`、75% `#0891B2→#0E7490`、100% `#155E75→#082F49`;深色整体提亮一档

### 排版
- 定义完整 Typography:displayLarge 40/48 Bold(进度数字)、titleLarge 20/28 SemiBold(卡片标题)、bodyMedium 14/20、labelSmall 11/16(时间戳)等

### 监测页布局(重构)
- 连接状态 + 设备在线**合并单条状态栏**(48dp、圆角 14dp、左 4dp success 高亮条,右侧设备在线小点)
- "选择训练比例"区:2×2 按钮,高 88dp、圆角 20dp、渐变背景、数字 40sp Bold + 副标(适应期/标准/负重较大/最大负重,25% 副标"适应期 · 负重较轻"等)
- 语音测试降级为 TextButton(VolumeUp 图标 + 文字)
- 训练进度卡:左 4dp 青→翠绿渐变高亮条(训练中脉冲)、大数字"第 X/3 次"、圆点序列(● ● ○)、时长/目标/步数、事件时间线
- 时间线:8dp 圆点(达到目标 = 翠绿)+ 2dp 细线,行高 36dp,时间戳 labelSmall

### 动效(3 处)
- 按钮按下:缩放 0.97 + 阴影 4→1dp,120ms FastOutSlowIn
- 训练开始:进度卡高亮条渐入 + 数字滚动 800ms
- 新事件条目:滑入 8dp + 透明度 0→1,240ms(仅最新一条)

### 文案润色(全量按 Hermes 提案 §6)
如:"已断开,点按重连"→"连接已断开,点击重试";"已达到目标重量"→"已达到训练目标";"完成一次重复"→"完成 1 次训练";"训练完成,已记录"→"本次训练已完成";"⚠ 平台连接不含加密…"→"平台连接为明文传输,请勿在公共网络使用"(emoji 换 M3 WarningAmber 图标);新增 ratio 副标/时间线标题等字符串

### 历史/设置轻量同步
- 历史页:卡片 16dp、间距 8dp、完成状态 AssistChip、显示步数
- 设置页:分组卡片化、Topic 说明文案
- 三页加 CenterAlignedTopAppBar

## 5. 分工

- **Hermes**:UI 改造实现(主题/监测页重构/历史设置同步/动效/文案)
- **主会话**:StepSensor 层、Room 迁移、设置页说明、测试(迁移/计步/VM)、审查与全量回归

## 6. 验证

- 单测:Room 迁移测试、StepSensor 逻辑(假传感器)、SessionTracker 计步、VM(步数进会话)、文案渲染
- 全量回归:testDebugUnitTest + assembleDebug
- 真机:验收清单更新(历史页不再闪退——需用户从 v1/v2 升级路径验证;计步需真机传感器)

## 7. 明确不做(P3 与激进方案)

- 环形仪表盘/滑块/两步交互(固件指令集约束,提案不推荐)
- 页面切换动效、卡片入场动画

# 康复训练助手 v4 — 点击反馈 + 主页面数据可视化

日期:2026-08-12 · 状态:已批准

## 背景

用户反馈:① 需要更强、更统一的按键点击反馈;② 主页面(监测页)空闲态信息密度低,希望加入数据可视化。UI 由 Hermes Agent 实现,数据聚合层由主会话实现。

## 1. 统一点击反馈(全 app)

- 共享 `pressFeedback` Modifier 扩展(涟漪 + 按压态:透明度/微缩);训练按钮保留现有缩放+阴影反馈
- 关键操作(四个训练比例按钮、语音测试)附加 `LocalHapticFeedback` 轻震动;普通元素(状态条/历史卡片/删除/开关)仅视觉
- 覆盖全部 ~23 个可交互元素;不新增依赖

## 2. 数据可视化(监测页,本地 Room,零依赖)

- **今日概览卡**:训练次数 / 总时长 / 总步数 三个大数字(今日 0 点起,实时刷新)
- **近 7 天趋势柱状图**:每天训练次数,Canvas 手绘柱状,今天高亮 + 日期标签 + 柱顶次数
- 布局:位于"选择训练比例"与"训练进度卡"之间,空闲/训练态均显示
- 空数据态文案:"暂无训练数据,开始第一次训练吧"

## 3. 架构

- `domain/SessionStatsAggregator`(纯函数):`todayStats(sessions, nowMillis, zoneId): TodayStats`、`weeklyStats(sessions, nowMillis, zoneId): List<DailyStat>`;nowMillis/ZoneId 注入可测
- `MonitorViewModel`:已有 `observeSessions()` 流 → map 聚合 → `MonitorUiState.todayStats/weeklyStats` 新字段
- 内存聚合(每次训练一条记录,量小);DAO 不动
- UI:`TodayStatsCard` / `WeeklyChartCard`(纯 Compose + Canvas,stringResource 文案)

## 4. 分工

- **Hermes**:pressFeedback 反馈系统 + 两个可视化卡实现 + 文案
- **主会话**:SessionStatsAggregator + VM 接线 + 单测 + 审查与全量回归

## 5. 验证

- 单测:聚合器(今日/跨日/时区/空数据)、VM 状态接入、回归全绿
- 仪器测试:可视化卡空态/有数据渲染断言(真机跑)
- 真机:反馈手感 + 可视化展示验收(ACCEPTANCE.md 追加)

# 康复训练助手 v2 — 对齐 Mind+ 固件协议设计

日期:2026-08-12 · 状态:已批准

## 背景

用户提供了掌控板实际烧录的 Mind+ 固件源码,其协议与 v1 设计的 JSON 协议完全不同。本版本将应用改造为与该固件完全对齐。

## 固件事实(源码实测,2026-08-12)

- 凭据(账号级,固件硬编码):`iot.dfrobot.com.cn:1883` / iot_id=`TyFA89yHR` / iot_pwd=`5318397328084412`
- Topic:`topics[] = {"wIOqDXyDg","q4F3DXyDg",...}`,`publish(topic_1,...)` 与订阅回调均使用 topic_1=`wIOqDXyDg`(app 只需此 topic)
- 指令(App→设备,单字符):`S` 语音问候"你好患者";`A/B/C/D` 开始训练,比例 25%/50%/75%/100%
- 状态(设备→App):`hello`(MQTT 连接成功后发布一次);`WA`(某次重复压力 > 体重×比例);`plus`(完成一次重复,语音"已达到训练的重量"+ LED 闪烁后)
- 训练循环 `DF_XunLian(ratio)`:阻塞直到 3 次重复完成;**无压力数据上报**(HX711 仅在设备本地 OLED 显示);**不支持中途停止指令**
- 设备体重由掌控板 P14/P15 按键 ±1kg 调节(默认 50),app 无法修改
- 训练中设备阻塞,期间收到的指令会排队到训练结束后处理

## 产品决策(用户确认)

| 决策 | 结论 |
|---|---|
| 监测页形态 | 远程训练控制台(比例按钮+事件流+进度) |
| 会话记录 | 事件驱动(发令开始,3×plus 结束) |
| 会话结束 | 3×plus 自动结束 + 10 分钟无事件超时(标"未完成") |
| 凭据默认值 | 留空自填(host/port 保留平台默认) |
| 体重字段 | 移除(固件按键调节) |

## 通信协议(短码,单 topic 双向)

- App→设备:`S` / `A` / `B` / `C` / `D`,QoS1,retained=false(避免设备开机误触发)
- 设备→App:`hello` / `WA` / `plus`,QoS0
- 其余载荷 → 无效帧计数(不崩溃)

## 领域模型变化

- 删除:`PressureSample`、`Thresholds`、`ThresholdCalculator`、体重百分比流、avg/peak
- 新增:`enum TrainingRatio(code, percent)`、`sealed interface DeviceEvent { Hello; RepReached; RepCompleted }`
- 修改:`TrainingSession(id, startTimeMillis, endTimeMillis, durationMillis, ratio, repsCompleted, completed)`;`SessionTracker` 事件驱动(仅 reps 计数)

## 界面

- **监测页**:连接状态条 → 设备上线徽标(hello)→ 四个比例按钮(训练中禁用)→ 问候测试按钮 → 训练进度卡(第 X/3 次 + 事件时间线)→ 会话统计(时长/比例)
- **历史页**:日期、目标比例、时长、次数、完成状态
- **设置页**:仅平台连接表单 + 测试连接 + 连接开关 + 明文警告 + 比例说明文案

## 会话状态机

Idle →(发 A/B/C/D)→ Training →(3×plus)完成落库 → Idle;或 10 分钟无 plus 超时落库(completed=false)。

## 验证

- 单测:短码编解码、事件驱动 SessionTracker、VM 状态机(虚拟时钟超时)、MQTT 收发、E2E 全闭环
- 真机:按 ACCEPTANCE.md 与固件联调(点 25% → 设备语音"开始训练" → 踩压达标 → 语音+LED → app 收到 WA/plus → 3 次后会话落库)

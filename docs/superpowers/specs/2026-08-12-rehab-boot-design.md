# 康复训练助手(智能康复靴)— 设计文档

日期:2026-08-12 · 状态:已批准

## 背景

南宁三中学生项目"智控负重脚踝康复训练靴":靴底半桥压力传感器(HX711 + 掌控板 ESP32)实时采集踩地压力,屏幕显示 + 语音按体重百分比(25%/50%/75%)阈值提醒。本应用为该设备的配套安卓端:远程监测负重、下发阈值、记录训练数据供医生评估。传输层为 DFRobot Easy IoT 平台 MQTT。

## 平台事实(2026-08-12 实地验证)

- Broker `iot.dfrobot.com.cn:1883`,仅明文 TCP,无 TLS/WebSocket,MQTT 3.1.1
- 认证:MQTT 用户名/密码 = 控制台 `Iot_id`/`Iot_pwd`(账号级,可"重新生成")
- Topic 为扁平随机字符串(即设备 ID,如 `BJpHJt1VW`),控制台创建,发布与订阅同一 topic
- Payload 任意字符串透传;QoS 0/1/2 与 retained 均支持
- 免费账号总存储 1 万条、每设备 1000 条 → 设备端建议 1~2Hz 上报
- 无公开 REST API;无官方 Android SDK → 用标准 MQTT 3.1.1 客户端(HiveMQ)

## 产品需求(用户确认)

| 决策 | 结论 |
|---|---|
| 产品形态 | 单设备训练助手:实时监测 + 阈值下发 + 会话记录 + 历史 |
| 连接生命周期 | 前台服务常驻(dataSync),断线自动重连 |
| 消息持久化 | 会话汇总落库(Room);实时采样不落时间序列 |
| 阈值模型 | 体重 kg × 百分比(25/50/75 可调)→ 下发 kg 值,设备语音提醒 |
| 训练记录 | 会话:时长 / 平均压力 / 峰值压力(不计步) |
| 设备配置 | 应用内手动配置(iot_id/iot_pwd/topic,全可编辑) |
| 语言 | 中文 |
| 命名 | 康复训练助手 / com.dfrobot.rehab |
| 通知 | 前台服务通知仅显示连接状态 |

## 通信协议(JSON 信封,单 topic 双向)

- 设备→App:`{"type":"data","p":12.5,"ts":1723456789}`(p=负重 kg)
- App→设备:`{"type":"config","p25":15.0,"p50":30.0,"p75":45.0}`,QoS 1 + retained(设备重连即收最新阈值)
- 非法/未知帧一律丢弃并计数
- 详见 docs/PROTOCOL.md(任务 14 落盘)

## 架构

单模块 Clean MVI(方案 A):

```
ui/            ← 纯 Compose,状态 + 回调
presentation/  ← ViewModel + UiState(StateFlow)/Intent(sealed)/Effect(Channel)
domain/        ← 模型 + use case + 仓库接口(不依赖 Android)
data/          ← HiveMQ MQTT + Room + DataStore + 协议编解码
core/          ← 前台服务(保活载体)+ 单例 MqttConnectionManager(唯一持连接)
```

关键模块:

- **MqttConnectionManager**(深模块):连接生命周期、指数退避重连(1s→60s)、`StateFlow<ConnectionState>`、`SharedFlow<MqttMessage>`、`suspend publishConfig()`、错误事件
- **SessionTracker**(纯类):会话状态机 Idle/Running/Paused,统计时长/平均/峰值
- **ProtocolCodec**:JSON 帧编解码,乱帧零容忍(返回 null)
- 仓库接口置于 domain,data 层实现;ViewModel 全部面向接口,可 fake 测试

## 界面(中文,Material 3)

- **监测页**:连接状态条(断开可点重连)→ 大数字负重 → 三档阈值进度条 → 30s Canvas 实时曲线 → 会话控件(开始/暂停/继续/结束)→ 无效帧计数调试信息
- **历史页**:会话列表(日期/时长/平均/峰值),点击详情,删除带确认
- **设置页**:平台连接(host/port/iot_id/iot_pwd/topic + 测试连接 + 启用连接开关)、训练参数(体重 kg、25/50/75%)、明文警告、关于
- 视觉基调:深青 #0E7490 主色 + 白底灰阶,大字号数字(医疗康复冷静风格)

## 验证

- 单测:阈值计算 / 协议编解码 / SessionTracker / 仓库 / ViewModel / 通知(Robolectric)
- MQTT:嵌入式 broker(HiveMQ 测试包)验证连接/重连/收发/retained
- 端到端:嵌入式 broker 模拟设备完整闭环(任务 14)
- 真机:用户按 docs/ACCEPTANCE.md 验收(真实平台 + 掌控板固件)

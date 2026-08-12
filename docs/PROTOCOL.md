# 康复训练助手 × 康复靴 通信协议(掌控板 Mind+ 固件实际协议)

本文档基于用户提供的实际烧录固件(Mind+ 生成,掌控板)编写,是应用与设备联调的唯一依据。

## 平台连接参数(固件实测)

| 参数 | 固件硬编码值 |
|---|---|
| Broker | `iot.dfrobot.com.cn` |
| 端口 | `1883`(仅明文 TCP,平台不支持 TLS) |
| iot_id | `TyFA89yHR`(账号级) |
| iot_pwd | `5318397328084412`(账号级) |
| Topic | `wIOqDXyDg`(固件 topics[0] = topic_1,发布与订阅同一 topic) |

> 凭据为账号级;固件中 `myIot.init("iot.dfrobot.com.cn","TyFA89yHR","5318397328084412","I0rtPo-DR",topics,1883)` 的 "I0rtPo-DR" 是数据通道参数,应用只需使用 topic_1 `wIOqDXyDg`。**换账号需同步修改固件与 App。**

## 消息协议(短码,单 topic 双向)

### 1. App → 设备:控制指令(QoS 1,retained=false)

| 载荷 | 含义 |
|---|---|
| `S` | 语音问候测试(固件播报"你好患者") |
| `A` | 开始训练,目标比例 25% |
| `B` | 开始训练,目标比例 50% |
| `C` | 开始训练,目标比例 75% |
| `D` | 开始训练,目标比例 100% |

**retained 必须为 false**:固件订阅时会立即收到 broker 保留的消息,开启 retained 会导致设备开机误触发训练。

### 2. 设备 → App:状态事件(QoS 0)

| 载荷 | 含义 |
|---|---|
| `hello` | 设备上电 / MQTT 连接成功后发布一次(用于判断设备在线) |
| `WA` | 某次重复的压力超过 体重×比例(固件语音"已达到训练的重量") |
| `plus` | 完成一次重复(语音+LED 闪烁后发布) |

### 3. 固件行为要点(影响 App 逻辑)

- **无压力数据上报**:HX711 读数仅在掌控板 OLED 本地显示,App 无法获取实时压力
- **训练循环阻塞**:`DF_XunLian(ratio)` 循环至 3 次重复完成;期间收到的指令会排队到训练结束后由 loop() 处理 → **App 在训练中必须禁用发令按钮**
- **训练结束**:3 次 `plus` 后自动结束;无中途停止指令
- **设备体重**:由掌控板 P14/P15 按键 ±1kg 调节(默认 50kg),App 无法修改
- 每次重复达标时可能连续发布多次 `WA`(压力持续高于阈值);`plus` 才是完成计数依据

### 4. 固件源码对照(节选)

```cpp
// 订阅回调:收到 App 指令
void obloqMqttEventTnhj9l(String& message) { mind_s_MQTT = message; }
// loop():指令分发
if (mind_s_MQTT == "S") { sstts.speak("你好患者"); ... }
if (mind_s_MQTT == "A") { DF_XunLian(0.25); ... }   // B→0.5  C→0.75  D→1.0
// DF_XunLian:循环至 3 次达标
if (mind_n_hx711 > mind_n_heavy * mind_n_BiZhi) {
    myIot.publish(topic_1, "WA");
    sstts.speak("已达到训练的重量");
    /* LED 闪烁 ×3 */
    myIot.publish(topic_1, "plus");
    mind_n_cnt += 1;
}
```

## App 端对应逻辑

- 监测页发令后进入"训练中",收到 3 次 `plus` 自动结束并记录会话(目标比例/次数/时长/完成状态)
- 10 分钟无 `plus` 事件 → 会话标记"未完成"结束(设备离线/未达标场景)
- `hello` → 设备上线徽标;连接断开 → 徽标复位
- 未知载荷 → 无效帧计数(零容忍,不崩溃)

## 平台限制提醒

- 免费账号总存储 1 万条消息,每设备最多 1000 条(短码协议消息量极小,无压力)
- 设备数上限 10 个/账号;`重新生成` Iot_id/Iot_pwd 会使固件与 App 全部失效
- 明文 1883:请勿在不可信网络(公共 WiFi)使用

# 康复训练助手 × 康复靴 通信协议(设备固件对接)

本文档定义康复靴(掌控板 ESP32)与安卓应用「康复训练助手」之间通过 DFRobot Easy IoT 平台的通信约定。固件按此文档实现。

## 平台连接参数

| 参数 | 值 |
|---|---|
| Broker | `iot.dfrobot.com.cn` |
| 端口 | `1883`(仅明文 TCP,平台不支持 TLS) |
| 协议 | MQTT 3.1.1 |
| 用户名 | 网页控制台账号的 `Iot_id` |
| 密码 | 网页控制台账号的 `Iot_pwd` |
| ClientID | 任意字符串(平台不校验) |

获取方式:注册并登录 https://iot.dfrobot.com.cn → 工作间 → 左侧显示账号级 `Iot_id`/`Iot_pwd`(点击眼睛图标显示);「添加新的设备」生成设备 Topic。

## Topic

**发布与订阅使用同一个 Topic**,即设备 ID(控制台生成的扁平随机字符串,如 `BJpHJt1VW`),无层级、无通配符。平台不接受客户端自造 Topic。

## 消息格式(JSON 信封,方向由 type 区分)

### 1. 设备 → 应用:压力上报

```json
{"type":"data","p":12.5,"ts":1723456789}
```

| 字段 | 含义 |
|---|---|
| `type` | 固定 `"data"` |
| `p` | 当前负重,单位 kg,浮点数(建议一位小数,如 12.5) |
| `ts` | 上报时 Unix 毫秒时间戳(可选,应用会以收到时刻兜底) |

**上报频率建议 1~2Hz**(步态周期尺度足够;注意平台免费账号每设备最多存储 1000 条消息,存满后不再存储,高频上报会快速耗尽配额)。

### 2. 应用 → 设备:阈值配置

```json
{"type":"config","p25":15.0,"p50":30.0,"p75":45.0}
```

| 字段 | 含义 |
|---|---|
| `type` | 固定 `"config"` |
| `p25`/`p50`/`p75` | 三档负重阈值,单位 kg(应用按 体重 × 百分比 换算) |

- 应用以 **QoS 1 + retained=true** 发布:设备每次(重新)订阅该 Topic 时,broker 会立即把最新保留的配置推给设备——**设备固件在 connect + subscribe 之后,应把收到的第一条 retained 消息当作当前阈值**。
- 语音提醒语义建议:达到 25% 档提醒一次(如"已达到目标负重"),达到 50% 提醒两次/更高音量,超过 75% 持续提示(如"负重过高,请注意")。

### 3. 双向过滤规则

- 设备固件:忽略所有 `type` 不是 `"config"` 的消息(应用发的 `data` 帧不要回显)。
- 应用:忽略所有 `type` 不是 `"data"` 的消息;任何非法 JSON / 未知 type / 负值一律丢弃并计数。

## MicroPython(uPyCraft / 掌控板)示例

```python
from umqtt.simple import MQTTClient
import ubinascii
import machine
import json

SERVER = "iot.dfrobot.com.cn"
IOT_ID = "你的Iot_id"      # 控制台账号级
IOT_PWD = "你的Iot_pwd"
TOPIC = "你的设备Topic"    # 如 BJpHJt1VW
CLIENT_ID = ubinascii.hexlify(machine.unique_id())  # 任意即可

p25 = p50 = p75 = 0.0  # 初始阈值,收到 config 帧后更新

def sub_cb(topic, msg):
    global p25, p50, p75
    try:
        frame = json.loads(msg)
        if frame.get("type") == "config":
            p25 = float(frame["p25"]); p50 = float(frame["p50"]); p75 = float(frame["p75"])
            print("阈值已更新:", p25, p50, p75)
    except Exception:
        pass  # 乱帧忽略

client = MQTTClient(CLIENT_ID, SERVER, port=1883, user=IOT_ID, password=IOT_PWD)
client.set_callback(sub_cb)
client.connect()
client.subscribe(TOPIC)

def report(pressure_kg):
    payload = '{"type":"data","p":%.1f}' % pressure_kg
    client.publish(TOPIC, payload)

# 主循环:每 500ms 上报一次压力,期间处理收到的 config
while True:
    client.check_msg()
    report(read_pressure_kg())   # read_pressure_kg() 由你的 HX711 读取代码实现
    time.sleep_ms(500)
```

> 注意:MicroPython 的 `umqtt.simple` 不支持 retained 标志位;若需固件端测试 retained 语义,用支持 retained 的客户端(如 Arduino PubSubClient 的 `publish(topic, payload, true)`)。应用下发本来就是 retained,固件只要在订阅后处理第一条 config 消息即可。

## 平台限制提醒

- 免费账号总存储 1 万条消息,每设备最多 1000 条;存满后新消息不再存储(实时推送不受影响,但控制台历史不再增长)。
- 设备数上限 10 个/账号。`重新生成` Iot_id/Iot_pwd 会使所有现有程序失效。
- 明文 1883:请勿在不可信网络(公共 WiFi)使用。

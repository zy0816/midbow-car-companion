# ZKVision 架构文档

> 车载情感机器人 App。把车机的语音助手状态与车辆事件，实时联动到 MIDBOW 机器人（身体动作 + 眼睛表情）。
> **任何大的改动前，必须先读本文档，遵守以下原则。**

包名：`com.midbows.zkvision`

---

## 一、设计原则（硬性要求）

1. **模块化 / 高内聚 / 低耦合**：每个包职责单一，对外只暴露最小接口。
2. **严格分层，依赖单向向下**（上层依赖下层，下层禁止反向依赖上层）：
   ```
   ui / service        组装层（Android 入口）
        │
   signal （输入）      把车机信号 → 统一事件
        │
   behavior （编排）    事件 → 动作序列、优先级、待机调度
        │
   ble （传输） + RobotController（高层动作 API）
        │
   protocol （纯逻辑）  帧构造，无任何 Android 依赖
   ```
3. **禁止重复代码**：任何已存在的公共方法必须复用，不得另写一份。
   - 帧构造只在 `protocol/` 一处实现。
   - 高层动作（转向/点头/表情）只在 `RobotController` 一处实现，`behavior` 与 `ui` 都调它，**禁止在别处手拼 byte[]**。
   - 日志、Hex 转换等放 `util/`，全项目共用。
4. **可测试性**：
   - `protocol/` 全是纯函数 → 100% 单元测试覆盖。
   - Android 依赖通过接口隔离（如 `RobotController` 接口 + `BleManager` 实现），便于 mock。
5. **可扩展性 / 利于长期演进**：
   - 新增一个行为 = 加一个 `Behavior` + 一条映射，不改引擎核心。
   - 新增一个信号源 = 实现 `SignalSource` 接口，不改其它 Monitor。
   - 新增一种机器人能力 = 在 `protocol/` 加帧 + `RobotController` 加方法。
6. **文件/函数拆小**：单文件 ≤ ~300 行，单函数 ≤ ~50 行，超了就拆。
7. **深浅色跟随车机系统**：主题用 `Theme.Material3.DayNight`，不写死颜色，跟随系统。

---

## 二、模块职责

### protocol/（纯逻辑，无 Android 依赖，全单测）
机器人协议的唯一真相来源。
- `MotionProtocol`：MIDBOW1S 运动板，FE55 帧。`FE 55 10 [cmd] 55 FE`。
  - 方向 上01/下02/左03/右04/中05；电机通电 CE / 断电 E4+CF；律动 减速E0/加速E1。
- `EyesProtocol`：ET-ROBOT-01 眼睛，a1 帧。`A1 [cmd] [seq] [len] [payload]`。
  - SET_RGB(0x14)：`A1 14 seq 04 B G R 亮度`（BGR 顺序）；PREV/NEXT 翻表情等。
- 输出 `byte[]`，输入是语义参数（方向枚举、RGB 值）。

### ble/（传输层）
- `BleManager`：双端点 GATT（运动 + 眼睛）、扫描、连接、自动重连、写队列 + 流控、订阅通知。
  - 设备名匹配：`MIDBOW1S`（运动）、`ET-ROBOT-01`（眼睛）。
  - 特征匹配（**实测正确值**）：
    - 运动：service `ae30`，写 `ae10`，通知 `ae05`。
    - 眼睛：service `3ac1937d`，写 `c02e69c2`，通知 `d914e6b6`。
- `RobotController`：高层动作 API（复用层）。把"语义动作"翻译成 protocol 帧并经 BleManager 发出。
  - `turnTo(seat)` / `nod()` / `shake()` / `center()` / `motorPower(on)` / `setEyeColor(r,g,b)` / `playEyeExpression(...)`。
  - **behavior 与 ui 只调用这里，绝不直接碰 byte[] 或 BleManager.sendXxx。**

### behavior/（编排层）
- `BehaviorEngine`：状态机 + 优先级仲裁 + 高优先级超时回落 + 待机调度。只调 `RobotController`。
- 优先级：IDLE < MUSIC < TTS < NAV < WELCOME < WAKEUP。
- `BehaviorLibrary`（可选）：把"某状态做什么动作组合"集中配置，便于调参。

### signal/（输入层）
- `SignalSource` 接口：`start() / stop()`，统一生命周期。
- `VoiceAssistantMonitor`：语音状态 + 分区唤醒座位（广播 + 音频焦点保底）。
- `MediaMonitor`：车机音乐播放状态 + 实时节拍（Visualizer，BPM 定时器保底）。
- `VehicleMonitor`：车门开关（CarPropertyManager / 车辆广播）。
- `NavMonitor`：导航转弯广播。
- 各 Monitor 把信号转成对 `BehaviorEngine` 的语义调用。

### service/
- `RobotService`：前台服务，持有引擎与所有 SignalSource，按设置开关。

### ui/
- `MainActivity`：控制台 + 手动测试台（连接状态、信号日志、手动触发表情/动作）。
- 设置页：各功能开关、灵敏度。DayNight 跟随系统。

### data/
- `SettingsManager`：SharedPreferences 封装，配置读写唯一入口。

### util/
- `SignalLogger`：统一日志广播（替换原来每个类各写一份 postLog）。
- `HexUtil`：hex/byte 互转。

---

## 三、关键约定

- **座位分区**：0=居中/全局，1=主驾，2=副驾，（后续可扩展 3/4 后排）。
- **信号日志广播**：`com.midbows.zkvision.ACTION_SIGNAL_LOG`，extra `log_message`。用于阶段0信号勘探与调试。
- **配置变更广播**：`com.midbows.zkvision.ACTION_CONFIG_CHANGED`。
- 车机生态为 ecarx 系；车机相关 action 名在阶段0勘探确认，集中放常量类，不散落各处。

## 四、测试策略

- `protocol/`：纯函数，JUnit 全覆盖（每条帧的字节、边界、seq 随机位除外用注入）。
- `RobotController`：mock `BleManager`，验证"语义动作 → 正确帧"。
- `BehaviorEngine`：mock `RobotController`，验证"事件 → 优先级仲裁 + 正确动作调用"。
- Monitor：解析逻辑（如 Intent extra → 座位）抽成纯函数单测；Android 收发部分集成测试/手测。

## 五、变更纪律

- 大改动前读本文档；新增能力按"协议→控制器→编排→信号"分层加，不跨层。
- 发现重复代码立即上提为公共方法。
- 每轮重构后跑单测 + 自检（逻辑/时序/遗漏/生效/清理），通过再继续。

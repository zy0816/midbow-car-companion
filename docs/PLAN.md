# ZKVision 实施计划

> 目标：车机语音助手状态 + 车辆事件，实时联动 MIDBOW 机器人（动作 + 表情），做车载情感陪伴（对标 NOMI）。
> 工作方式：逐项执行，重构补单测，每轮完成后验证，有问题继续改。原则见 `ARCHITECTURE.md`。

## 背景 / 已确认事实

- 机器人 = 一台，两个 BLE 端点：
  - **运动板 MIDBOW1S**：FE55 协议，service `ae30` / 写 `ae10` / 通知 `ae05`。**已实测可驱动运动**。
  - **眼睛 ET-ROBOT-01**：a1 协议，service `3ac1937d` / 写 `c02e69c2` / 通知 `d914e6b6`。
- FE55 关键帧：方向 上01/下02/左03/右04/中05；电机通电 `FE5510CE55FE`；律动 减速 `E0`/加速 `E1`。
- 信号源（全部来自车机）：分区唤醒（座位）、车机音乐播放（实时律动）、车门开关（上车问候）、导航广播（转弯）。
- **已有骨架在旧项目 `~/遥控器`（包 com.airmouse.tv），混着 AirMouse 鼠标残留**：本计划将其中机器人代码迁入本项目并重构，丢弃鼠标代码。已有骨架的问题：
  1. BLE 特征匹配反了（把 service 当写特征）。
  2. BehaviorEngine 发的是占位假指令（2字节），不是真实 FE55/a1 帧。
  3. 每个类各写一份 `postLog`，重复代码。
  4. 无任何单元测试。

---

## 阶段总览

- 阶段 0：项目骨架（gradle / manifest / 签名 / DayNight 主题）
- 阶段 1：protocol 层（帧构造）+ 单测 ← **先做，纯逻辑最稳**
- 阶段 2：ble 层（BleManager 修正 + RobotController 高层 API）+ 单测
- 阶段 3：behavior 层（引擎用 RobotController 重写）+ 单测
- 阶段 4：signal 层（4 个 Monitor + SignalSource 接口 + SignalLogger 复用）
- 阶段 5：service + ui（控制台/测试台/设置，DayNight）
- 阶段 6：车机信号勘探（语音分区/车门/导航/音乐 的真实 action 与字段）
- 阶段 7：联调打磨（音乐实时律动、导航联动、上车问候、参数调优）

每个阶段是一组可勾选任务，完成即验证。

---

## 阶段 0 — 项目骨架
- [ ] `settings.gradle` / 根 `build.gradle` / `app/build.gradle`（minSdk 24，平台签名，JUnit + Mockito + Robolectric 测试依赖）
- [ ] `AndroidManifest.xml`（权限：BLE、前台服务；注册 RobotService）
- [ ] 平台签名 keystore（平台 key：CN=Android）
- [ ] DayNight 主题（`Theme.Material3.DayNight`，跟随系统深浅）
- [ ] 验证：`./gradlew assembleDebug` 通过

## 阶段 1 — protocol（纯逻辑 + 全单测）
- [ ] `util/HexUtil`
- [ ] `MotionProtocol`：方向/电机/律动 帧构造（FE55）
- [ ] `EyesProtocol`：SET_RGB / 表情翻页（a1）
- [ ] 单测：每条帧字节精确断言、边界值
- [ ] 验证：`./gradlew test` 全绿

## 阶段 2 — ble（传输 + 复用 API）
- [ ] 迁移 `BleManager`，**修正特征匹配**（写 ae10 / c02e69c2，通知 ae05 / d914e6b6），双端点、写队列、流控、自动重连
- [ ] `RobotController` 接口 + 实现：turnTo/nod/shake/center/motorPower/setEyeColor/playEyeExpression（调 protocol + BleManager）
- [ ] 单测：mock BleManager，验证语义动作 → 正确帧
- [ ] 验证：单测 + 真机连接冒烟（可选）

## 阶段 3 — behavior（编排）
- [ ] `BehaviorEngine` 重写：状态机/优先级/超时回落/待机调度，**只调 RobotController**
- [ ] 行为映射：唤醒转向、聆听/思考/播报、上车问候、导航转弯、音乐律动、待机眨眼微动
- [ ] 单测：mock RobotController，验证事件→优先级→动作
- [ ] 验证：单测全绿

## 阶段 4 — signal（输入）
- [ ] `SignalSource` 接口；`util/SignalLogger` 复用（删除各处重复 postLog）
- [ ] 迁移 4 个 Monitor，解析逻辑抽纯函数单测
- [ ] 验证：单测 + 字段解析测试

## 阶段 5 — service + ui
- [ ] `RobotService` 前台服务
- [ ] `MainActivity` 控制台 + 手动测试台 + 信号日志
- [ ] 设置页（功能开关），DayNight
- [ ] **UI 出图给用户确认后再实现**
- [ ] 验证：装车机自测

## 阶段 6 — 车机信号勘探
- [ ] 语音助手：包名 + 状态机制 + 分区/座位字段（用测试台日志抓真实 Intent extra）
- [ ] 车门：CarPropertyManager 门 PROP / 车辆广播
- [ ] 导航：转弯广播 action 与字段
- [ ] 音乐：MediaSession + Visualizer 节拍可行性
- [ ] 产出：真实 action/字段常量表，回填代码

## 阶段 7 — 联调打磨
- [ ] 音乐实时律动跟随车机播放
- [ ] 导航/上车问候联动
- [ ] 动作/表情编排调优
- [ ] 记录版本变更

---

## 验收标准（每轮）
1. 编译通过；2. 相关单测全绿；3. 自检（逻辑/时序/遗漏/生效/清理）；4. 无重复代码、无跨层依赖。

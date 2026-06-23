package com.midbows.zkvision.behavior;

import android.os.Handler;
import android.os.Looper;

import com.midbows.zkvision.ble.RobotController;
import com.midbows.zkvision.protocol.MotionProtocol;
import com.midbows.zkvision.util.RobotLog;

import java.util.Random;

/**
 * 行为引擎：接收语义事件，经 {@link PriorityArbiter} 仲裁后用 {@link RobotChoreographer} 编排动作。
 *
 * <p>不构帧、不发字节，只做「事件 → 优先级 → 编排」的接线。高优先级临时事件超时自动回落 IDLE。
 */
public final class BehaviorEngine {

    private static final String TAG = "BehaviorEngine";
    private static final long HIGH_PRIORITY_TIMEOUT_MS = 4000;
    /** 迎宾安全兜底：正常应由「车门关闭」事件回正；万一漏收关门信号，最长保持这么久后强制回正。 */
    private static final long WELCOME_SAFETY_MS = 30000;
    /** 转向灯保持时长：每次闪烁刷新，停止闪烁后过这么久回正。 */
    private static final long TURN_SIGNAL_HOLD_MS = 2000;
    private static final long IDLE_MIN_MS = 5000;
    private static final long IDLE_VAR_MS = 5000;
    private static final int AMBIENT_BRIGHTNESS = 200;

    private final PriorityArbiter arbiter = new PriorityArbiter();
    private final RobotChoreographer choreo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    /**
     * 静息底色（机器人底部氛围灯）。持久型车态（氛围灯同步/驾驶模式/昼夜/冷热/充电）改写它，
     * 瞬时高优先级事件结束后回落到此底色，而非生硬熄灭。默认取 IDLE 调色。
     */
    private int baseR = EyeMood.IDLE.r;
    private int baseG = EyeMood.IDLE.g;
    private int baseB = EyeMood.IDLE.b;
    private int baseBrightness = EyeMood.IDLE.brightness;

    /**
     * 当前是否正在播放音乐（持久态）。瞬时高优先级事件（迎宾/转向/急刹等）打断音乐律动后，
     * 事件结束应**续上音乐律动**而非生硬居中熄灭；此标记让回落时知道该回律动还是回静息底色。
     */
    private boolean musicPlaying;

    public BehaviorEngine(RobotController robot) {
        this.choreo = new RobotChoreographer(robot);
    }

    public void start() {
        RobotLog.d(TAG, "行为引擎启动");
        handler.removeCallbacks(idleRunnable);
        handler.post(idleRunnable);
    }

    public void stop() {
        RobotLog.d(TAG, "行为引擎停止");
        handler.removeCallbacks(idleRunnable);
    }

    // ---------------- 语义事件入口 ----------------

    /** 语音唤醒并转向说话人座位：1=主驾 2=副驾 0=居中。 */
    public void onVoiceWakeup(int seat) {
        if (arbiter.accept(PriorityArbiter.WAKEUP)) {
            RobotLog.d(TAG, "唤醒，转向座位 " + seat);
            choreo.turnToSeat(seat);
            choreo.mood(EyeMood.HAPPY);
            scheduleHighPriorityReset(PriorityArbiter.WAKEUP);
        }
    }

    /** 语音助手状态：listening / thinking / speaking / idle。 */
    public void onVoiceStateChanged(String state) {
        if ("listening".equalsIgnoreCase(state)) {
            if (arbiter.accept(PriorityArbiter.TTS)) {
                choreo.center();
                choreo.mood(EyeMood.LISTENING);
            }
        } else if ("thinking".equalsIgnoreCase(state)) {
            if (arbiter.accept(PriorityArbiter.TTS)) {
                choreo.shake();
                choreo.mood(EyeMood.THINKING);
            }
        } else if ("speaking".equalsIgnoreCase(state)) {
            if (arbiter.accept(PriorityArbiter.TTS)) {
                choreo.nod();
                choreo.mood(EyeMood.SPEAKING);
            }
        } else if ("idle".equalsIgnoreCase(state)) {
            arbiter.resetIfAt(PriorityArbiter.TTS);
        }
    }

    /**
     * 上车问候（车门打开触发）：头转向开门侧 + 问候声，并**保持转向直到车门关闭**
     * （由 {@link #onDoorClosed()} 回正），仅在漏收关门信号时才靠安全兜底超时回正。
     * 1=主驾(左) 2=副驾(右) 0=居中。
     */
    public void onDoorWelcome(int seat) {
        if (arbiter.accept(PriorityArbiter.WELCOME)) {
            RobotLog.d(TAG, "上车问候 座位 " + seat);
            choreo.welcomeTurn(seat);
            choreo.mood(EyeMood.HAPPY);
            scheduleHighPriorityReset(PriorityArbiter.WELCOME, WELCOME_SAFETY_MS);
        }
    }

    /** 车门全部关闭：迎宾结束，回落——若音乐仍在放则续上律动，否则头回正、回静息底色。 */
    public void onDoorClosed() {
        if (arbiter.resetIfAt(PriorityArbiter.WELCOME)) {
            RobotLog.d(TAG, "车门关闭，回落");
            handler.removeCallbacks(resetRunnable);
            resumeRestingState();
        }
    }

    /** 导航转弯联动：left / right。 */
    public void onNavTurn(String direction) {
        if (arbiter.accept(PriorityArbiter.NAV)) {
            boolean left = "left".equalsIgnoreCase(direction);
            RobotLog.d(TAG, "导航转向 " + direction);
            choreo.turn(left);
            choreo.mood(EyeMood.ALERT);
            scheduleHighPriorityReset(PriorityArbiter.NAV);
        }
    }

    /** 车机开始播放音乐：开启固件自适应律动（摇头晃脑）。 */
    public void onMusicStart() {
        RobotLog.d(TAG, "音乐开始，律动");
        musicPlaying = true;
        choreo.musicGrooveOn();
        if (arbiter.accept(PriorityArbiter.MUSIC)) {
            choreo.mood(EyeMood.MUSIC);
        }
    }

    /** 车机停止播放音乐：关闭律动并回落底色。 */
    public void onMusicStop() {
        RobotLog.d(TAG, "音乐停止");
        musicPlaying = false;
        choreo.musicGrooveOff();
        if (arbiter.resetIfAt(PriorityArbiter.MUSIC)) {
            applyBase();
        }
    }

    /**
     * 运动板（重）连成功后复位到当前应有状态。
     *
     * <p>车机锁车睡眠会先断蓝牙，律动「关闭」命令发不出去，而运动板的自维持律动会一直跳。
     * 重连后据当前音乐状态纠正：没在放音乐就主动停律动并居中回底色，杀掉遗留律动；仍在放
     * 就续上律动。避免「都锁车了机器人还在跳」。
     */
    public void onMotionLinkReady() {
        RobotLog.d(TAG, "运动板已就绪，复位状态 musicPlaying=" + musicPlaying);
        if (musicPlaying) {
            choreo.musicGrooveOn();
        } else {
            choreo.musicGrooveOff();
            choreo.center();
            applyBase();
        }
    }

    // ---------------- 车辆状态联动（v3.4） ----------------

    /** 氛围灯同步：底部灯跟随车机氛围灯颜色（持久底色）。 */
    public void onAmbientColor(int r, int g, int b) {
        RobotLog.d(TAG, "氛围灯同步 rgb=" + r + "," + g + "," + b);
        setBase(r, g, b, AMBIENT_BRIGHTNESS);
    }

    /** 氛围灯关闭：底色回落到默认 IDLE。 */
    public void onAmbientOff() {
        setBase(EyeMood.IDLE.r, EyeMood.IDLE.g, EyeMood.IDLE.b, EyeMood.IDLE.brightness);
    }

    /** 驾驶模式人格：底色跟随驾驶模式（持久底色）。 */
    public void onDrivePersonality(int r, int g, int b, int brightness, boolean energetic) {
        RobotLog.d(TAG, "驾驶模式人格 energetic=" + energetic);
        setBase(r, g, b, brightness);
    }

    /** 昼夜：夜间犯困（暗），白天明亮。持久底色。 */
    public void onDayNight(boolean night) {
        RobotLog.d(TAG, night ? "夜间犯困" : "白天精神");
        EyeMood m = night ? EyeMood.NIGHT : EyeMood.DAY;
        setBase(m.r, m.g, m.b, m.brightness);
    }

    /** 急加速：身体后仰。 */
    public void onHardAccel() {
        if (arbiter.accept(PriorityArbiter.ACCEL)) {
            RobotLog.d(TAG, "急加速，后仰");
            choreo.lean(true);
            choreo.mood(EyeMood.ALERT);
            scheduleHighPriorityReset(PriorityArbiter.ACCEL);
        }
    }

    /** 急刹车：身体前倾。 */
    public void onHardBrake() {
        if (arbiter.accept(PriorityArbiter.ACCEL)) {
            RobotLog.d(TAG, "急刹车，前倾");
            choreo.lean(false);
            choreo.mood(EyeMood.ALERT);
            scheduleHighPriorityReset(PriorityArbiter.ACCEL);
        }
    }

    /** 冷反应：冰蓝 + 哆嗦（持久底色直到回暖）。 */
    public void onCabinCold() {
        RobotLog.d(TAG, "车内偏冷，哆嗦");
        setBase(EyeMood.COLD.r, EyeMood.COLD.g, EyeMood.COLD.b, EyeMood.COLD.brightness);
        if (arbiter.current() == PriorityArbiter.IDLE) {
            choreo.shiver();
        }
    }

    /** 热反应：灼红（持久底色直到回凉）。 */
    public void onCabinHot() {
        RobotLog.d(TAG, "车内偏热");
        setBase(EyeMood.HOT.r, EyeMood.HOT.g, EyeMood.HOT.b, EyeMood.HOT.brightness);
    }

    /** 温度回到舒适区：底色回落默认。 */
    public void onCabinNormal() {
        onAmbientOff();
    }

    /** 空调调高设定温度：点头 + 开心表情（"嗯，暖一点更舒服"）。瞬时互动，不碰氛围灯。 */
    public void onAcWarmer() {
        if (arbiter.accept(PriorityArbiter.ACCEL)) {
            RobotLog.d(TAG, "空调调高，点头开心");
            choreo.nod();
            choreo.expression(MotionProtocol.EXPR_HAPPY);
            scheduleHighPriorityReset(PriorityArbiter.ACCEL);
        }
    }

    /** 空调调低设定温度：哆嗦 + 装酷表情（"凉快～"）。瞬时互动，不碰氛围灯。
     *  注：好冷专属表情字节未公开，实车扫到后可替换为更贴切的冷表情。 */
    public void onAcCooler() {
        if (arbiter.accept(PriorityArbiter.ACCEL)) {
            RobotLog.d(TAG, "空调调低，哆嗦装酷");
            choreo.shiver();
            choreo.expression(MotionProtocol.EXPR_COOL);
            scheduleHighPriorityReset(PriorityArbiter.ACCEL);
        }
    }

    /** 碰撞预警：受惊后仰 + 红闪（最高优先级）。 */
    public void onCollisionWarn() {
        if (arbiter.accept(PriorityArbiter.COLLISION)) {
            RobotLog.d(TAG, "碰撞预警，受惊");
            choreo.recoil();
            choreo.mood(EyeMood.ALERT);
            scheduleHighPriorityReset(PriorityArbiter.COLLISION);
        }
    }

    /** 盲区扭头：朝告警侧探头 + 警示黄。{@code left} true=左侧。 */
    public void onBlindSpot(boolean left) {
        if (arbiter.accept(PriorityArbiter.BLINDSPOT)) {
            RobotLog.d(TAG, "盲区告警 " + (left ? "左" : "右"));
            choreo.peek(left);
            choreo.mood(EyeMood.CAUTION);
            scheduleHighPriorityReset(PriorityArbiter.BLINDSPOT);
        }
    }

    /** 转向灯：朝转向侧明显转头并保持，闪烁期间持续刷新，停止后回正。 */
    public void onTurnSignal(boolean left) {
        if (arbiter.accept(PriorityArbiter.PEEK)) {
            RobotLog.d(TAG, "转向灯转头 " + (left ? "左" : "右"));
            choreo.lookToward(left);
            scheduleHighPriorityReset(PriorityArbiter.PEEK, TURN_SIGNAL_HOLD_MS);
        }
    }

    /** 胎压告警：摇头示警 + 红。 */
    public void onTireWarning() {
        if (arbiter.accept(PriorityArbiter.TIRE)) {
            RobotLog.d(TAG, "胎压告警");
            choreo.shake();
            choreo.mood(EyeMood.ALERT);
            scheduleHighPriorityReset(PriorityArbiter.TIRE);
        }
    }

    /** 充电：进入睡眠（暗暖、静止、慢）；停止充电回落默认。持久底色。 */
    public void onCharging(boolean charging) {
        RobotLog.d(TAG, charging ? "充电中，睡觉" : "停止充电，醒来");
        if (charging) {
            setBase(EyeMood.SLEEP.r, EyeMood.SLEEP.g, EyeMood.SLEEP.b, EyeMood.SLEEP.brightness);
            choreo.center();
        } else {
            onAmbientOff();
        }
    }

    /** 充满电：醒来庆祝（点头 + 开心）。 */
    public void onChargeFull() {
        if (arbiter.accept(PriorityArbiter.WELCOME)) {
            RobotLog.d(TAG, "充满电，醒来庆祝");
            choreo.nod();
            choreo.mood(EyeMood.HAPPY);
            scheduleHighPriorityReset(PriorityArbiter.WELCOME);
        }
    }

    // ---------------- 场景助手：自定义规则通用动作入口 ----------------

    /** 自定义规则触发的瞬时动作优先级：高于音乐/导航，但不压制盲区/碰撞等安全类。 */
    public static final int CUSTOM_PRIORITY = 33;

    /**
     * 执行一个由场景助手规则指定的动作。
     *
     * <p>{@code actionKey} 取 {@code ActionCatalog.KEY_*}；瞬时动作经仲裁后编排并定时回落，
     * 持久型（设底色/恢复默认）直接改静息底色。{@code rgb} 仅设色/闪色类动作使用（0xRRGGBB）。
     */
    public void runAction(String actionKey, int rgb) {
        if (actionKey == null) {
            return;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        switch (actionKey) {
            case "set_color":
                RobotLog.d(TAG, "规则动作：设底色 " + r + "," + g + "," + b);
                setBase(r, g, b, AMBIENT_BRIGHTNESS);
                break;
            case "reset_base":
                RobotLog.d(TAG, "规则动作：恢复默认底色");
                onAmbientOff();
                break;
            case "groove_on":
                RobotLog.d(TAG, "规则动作：律动开");
                choreo.musicGrooveOn();
                break;
            case "groove_off":
                RobotLog.d(TAG, "规则动作：律动关");
                choreo.musicGrooveOff();
                choreo.center();
                break;
            default:
                runMomentary(actionKey, rgb);
                break;
        }
    }

    /** 瞬时动作：仲裁通过后编排，固定时长后回落。 */
    private void runMomentary(String actionKey, int rgb) {
        if (!arbiter.accept(CUSTOM_PRIORITY)) {
            return;
        }
        RobotLog.d(TAG, "规则动作：" + actionKey);
        long hold = HIGH_PRIORITY_TIMEOUT_MS;
        // 表情动作：键形如 expr_0A，解析出 FE55 表情字节直接播放。
        if (actionKey.startsWith("expr_")) {
            try {
                byte cmd = (byte) Integer.parseInt(actionKey.substring(5), 16);
                choreo.expression(cmd);
                scheduleHighPriorityReset(CUSTOM_PRIORITY, hold);
            } catch (NumberFormatException e) {
                arbiter.resetIfAt(CUSTOM_PRIORITY);
            }
            return;
        }
        switch (actionKey) {
            case "nod":
                choreo.nod();
                break;
            case "shake":
                choreo.shake();
                break;
            case "turn_left":
                choreo.turn(true);
                break;
            case "turn_right":
                choreo.turn(false);
                break;
            case "hold_left":
                choreo.lookToward(true);
                hold = TURN_SIGNAL_HOLD_MS;
                break;
            case "hold_right":
                choreo.lookToward(false);
                hold = TURN_SIGNAL_HOLD_MS;
                break;
            case "center":
                choreo.center();
                break;
            case "lean_back":
                choreo.lean(true);
                break;
            case "lean_fwd":
                choreo.lean(false);
                break;
            case "peek_left":
                choreo.peek(true);
                break;
            case "peek_right":
                choreo.peek(false);
                break;
            case "shiver":
                choreo.shiver();
                break;
            case "recoil":
                choreo.recoil();
                break;
            case "wobble":
                choreo.wobble();
                break;
            case "flash_color":
                choreo.moodRgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
                break;
            default:
                // 未知动作：撤销刚抬高的优先级，避免卡住。
                arbiter.resetIfAt(CUSTOM_PRIORITY);
                return;
        }
        scheduleHighPriorityReset(CUSTOM_PRIORITY, hold);
    }

    // ---------------- 底色管理 ----------------

    private synchronized void setBase(int r, int g, int b, int brightness) {
        baseR = r;
        baseG = g;
        baseB = b;
        baseBrightness = brightness;
        if (arbiter.current() == PriorityArbiter.IDLE) {
            applyBase();
        }
    }

    private void applyBase() {
        choreo.moodRgb(baseR, baseG, baseB, baseBrightness);
    }

    /**
     * 瞬时高优先级事件结束后的统一回落：音乐仍在放就续上律动 + 音乐情绪色（被打断后继续），
     * 否则头居中并回静息底色。集中在此，所有打断型事件回落时一致处理「被打断的音乐要续上」。
     */
    private void resumeRestingState() {
        if (musicPlaying) {
            RobotLog.d(TAG, "回落：音乐仍在放，续上律动");
            choreo.musicGrooveOn();
            if (arbiter.accept(PriorityArbiter.MUSIC)) {
                choreo.mood(EyeMood.MUSIC);
            }
        } else {
            choreo.center();
            applyBase();
        }
    }

    // ---------------- 时序回落 ----------------

    private int pendingResetPriority = PriorityArbiter.IDLE;

    private final Runnable resetRunnable = new Runnable() {
        @Override
        public void run() {
            if (arbiter.resetIfAt(pendingResetPriority)) {
                RobotLog.d(TAG, "高优先级超时，回落");
                resumeRestingState();
            }
        }
    };

    /**
     * 安排高优先级事件超时回落。单 token 复用：每次调用先撤销上一次未触发的回落，
     * 避免旧 Runnable 堆积后乱发 center（曾导致连续 05 抖动）。accept() 仅允许 ≥ 当前优先级，
     * 故最新一次即当前最高，resetIfAt(最新) 正确。
     */
    private void scheduleHighPriorityReset(int priority) {
        scheduleHighPriorityReset(priority, HIGH_PRIORITY_TIMEOUT_MS);
    }

    private void scheduleHighPriorityReset(int priority, long timeoutMs) {
        pendingResetPriority = priority;
        handler.removeCallbacks(resetRunnable);
        handler.postDelayed(resetRunnable, timeoutMs);
    }

    private final Runnable idleRunnable = new Runnable() {
        @Override
        public void run() {
            if (arbiter.current() == PriorityArbiter.IDLE) {
                int action = random.nextInt(3);
                if (action == 0) {
                    applyBase();
                } else if (action == 1) {
                    choreo.microMotion();
                }
                // action == 2: 静止呼吸，不动
            }
            handler.postDelayed(this, IDLE_MIN_MS + random.nextInt((int) IDLE_VAR_MS));
        }
    };
}

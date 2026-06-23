package com.midbows.zkvision.behavior;

import android.os.Handler;
import android.os.Looper;

import com.midbows.zkvision.ble.RobotController;
import com.midbows.zkvision.protocol.MotionProtocol;

/**
 * 把 {@link RobotController} 的原语组合成有名字的编排动作（点头/摇头/转向/眨眼）。
 *
 * <p>编排里的时序（先低头再抬头再居中等）集中在这一层，BehaviorEngine 只调用语义方法，
 * RobotController 只发单帧；三层各司其职，避免时序逻辑散落。
 */
public final class RobotChoreographer {

    private static final long STEP_MS = 300;

    private final RobotController robot;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public RobotChoreographer(RobotController robot) {
        this.robot = robot;
    }

    /** 设置眼睛情绪色。 */
    public void mood(EyeMood m) {
        robot.setEyeColor(m.r, m.g, m.b, m.brightness);
    }

    /** 设置任意 RGB 底部氛围色（用于跟随车机氛围灯/驾驶模式人格）。 */
    public void moodRgb(int r, int g, int b, int brightness) {
        robot.setEyeColor(r, g, b, brightness);
    }

    /** 转向座位：1=主驾(左) 2=副驾(右) 其它=居中。 */
    public void turnToSeat(int seat) {
        if (seat == 1) {
            robot.hold(MotionProtocol.DIR_LEFT);
        } else if (seat == 2) {
            robot.hold(MotionProtocol.DIR_RIGHT);
        } else {
            robot.center();
        }
    }

    /**
     * 上车迎宾：头转向开门侧并**保持转向**（不自动回正，由车门关闭事件回正）。
     * 1=主驾(左) 2=副驾(右) 其它=居中。
     *
     * <p>按用户要求只做摇头动作、不发声（去掉了原来的问候声 playSound）。
     */
    public void welcomeTurn(int seat) {
        turnToSeat(seat);
    }

    /** 朝某侧明显转头并保持（转向灯用），不自动回正。 */
    public void lookToward(boolean left) {
        robot.hold(left ? MotionProtocol.DIR_LEFT : MotionProtocol.DIR_RIGHT);
    }

    /** 转向导航方向。 */
    public void turn(boolean left) {
        robot.hold(left ? MotionProtocol.DIR_LEFT : MotionProtocol.DIR_RIGHT);
        handler.postDelayed(robot::center, 2 * STEP_MS);
    }

    /** 点头（下→上→中）。 */
    public void nod() {
        robot.hold(MotionProtocol.DIR_DOWN, 5);
        handler.postDelayed(() -> robot.hold(MotionProtocol.DIR_UP, 5), STEP_MS);
        handler.postDelayed(robot::center, 2 * STEP_MS);
    }

    /** 摇头（左→右→中）。 */
    public void shake() {
        robot.hold(MotionProtocol.DIR_LEFT, 5);
        handler.postDelayed(() -> robot.hold(MotionProtocol.DIR_RIGHT, 5), STEP_MS);
        handler.postDelayed(robot::center, 2 * STEP_MS);
    }

    /** 居中复位。 */
    public void center() {
        robot.center();
    }

    /** 音乐律动开：交给运动板固件自适应律动（随音乐摇头晃脑），不逐拍干预。 */
    public void musicGrooveOn() {
        robot.adaptiveRhythm(true);
    }

    /** 音乐律动关：关闭固件自适应律动并居中复位。 */
    public void musicGrooveOff() {
        robot.adaptiveRhythm(false);
        robot.center();
    }

    /** 播放眼屏表情（厂家 FE55 表情字节，由运动板下发）。 */
    public void expression(byte cmd) {
        robot.playExpression(cmd);
    }

    /** 摇头晃脑：运动板内置摆头动作（FE55 0x25）。 */
    public void wobble() {
        robot.wobble();
    }

    /** 待机微动。 */
    public void microMotion() {
        robot.look(MotionProtocol.DIR_LEFT);
        handler.postDelayed(robot::center, STEP_MS / 2);
    }

    /** 身体前倾/后仰：急加速后仰(back=上)、急刹前倾(back=下)。 */
    public void lean(boolean back) {
        robot.hold(back ? MotionProtocol.DIR_UP : MotionProtocol.DIR_DOWN, 6);
        handler.postDelayed(robot::center, 2 * STEP_MS);
    }

    /** 朝某侧快速探头再收回（转向灯/盲区）。 */
    public void peek(boolean left) {
        robot.look(left ? MotionProtocol.DIR_LEFT : MotionProtocol.DIR_RIGHT);
        handler.postDelayed(robot::center, STEP_MS);
    }

    /** 哆嗦：左右快速抖动几下（冷反应）。 */
    public void shiver() {
        robot.look(MotionProtocol.DIR_LEFT);
        handler.postDelayed(() -> robot.look(MotionProtocol.DIR_RIGHT), STEP_MS / 3);
        handler.postDelayed(() -> robot.look(MotionProtocol.DIR_LEFT), 2 * STEP_MS / 3);
        handler.postDelayed(() -> robot.look(MotionProtocol.DIR_RIGHT), STEP_MS);
        handler.postDelayed(robot::center, 4 * STEP_MS / 3);
    }

    /** 受惊后仰（碰撞预警）：强力上仰再复位。 */
    public void recoil() {
        robot.hold(MotionProtocol.DIR_UP, 10);
        handler.postDelayed(robot::center, 3 * STEP_MS);
    }
}

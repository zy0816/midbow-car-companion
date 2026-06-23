package com.midbows.zkvision.ble;

import com.midbows.zkvision.protocol.EyesProtocol;
import com.midbows.zkvision.protocol.MotionProtocol;

import java.util.Random;

/**
 * 机器人高层动作出口：把语义动作翻译成 protocol 帧并经 {@link BleManager} 下发。
 *
 * <p>全项目唯一的「动作 → 字节」映射点。behavior / signal / ui 等上层只调用本类方法，
 * 不直接构帧、不直接调用 BleManager 发字节（禁止重复编写代码）。
 */
public final class RobotController {

    /** 运动板单次脉冲过短，按住效果需重复下发；这是实测确认的默认重复次数。 */
    private static final int DEFAULT_HOLD_REPEAT = 10;

    private final BleManager ble;
    private final Random random = new Random();

    public RobotController(BleManager ble) {
        this.ble = ble;
    }

    // ---------------- 运动板（FE55） ----------------

    public void motorOn() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.MOTOR_ON));
    }

    public void motorOff() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.MOTOR_OFF_PRE));
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.MOTOR_OFF));
    }

    /** 朝某方向运动一次。{@code dir} 取 {@link MotionProtocol} 的 DIR_* 常量。 */
    public void look(byte dir) {
        ble.sendMotion(MotionProtocol.frame(dir));
    }

    /** 按住式运动：重复下发同一方向以维持动作。 */
    public void hold(byte dir) {
        hold(dir, DEFAULT_HOLD_REPEAT);
    }

    public void hold(byte dir, int repeat) {
        byte[] frame = MotionProtocol.frame(dir);
        for (int i = 0; i < repeat; i++) {
            ble.sendMotion(frame);
        }
    }

    public void center() {
        look(MotionProtocol.DIR_CENTER);
    }

    /** 播放问候/提示声（运动板 SOUND_ON）。具体音效由固件决定，需实车确认。 */
    public void playSound() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.SOUND_ON));
    }

    public void rhythmFaster() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.RHYTHM_FASTER));
    }

    public void rhythmSlower() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.RHYTHM_SLOWER));
    }

    public void adaptiveRhythm(boolean on) {
        byte cmd = on ? MotionProtocol.RHYTHM_ADAPTIVE_ON : MotionProtocol.RHYTHM_ADAPTIVE_OFF;
        ble.sendMotion(MotionProtocol.frame(cmd));
    }

    // ---------------- 运动板：复刻小程序的其余功能 ----------------

    /** 声音开关（提示/语音音效）。 */
    public void soundEnabled(boolean on) {
        ble.sendMotion(MotionProtocol.frame(on ? MotionProtocol.SOUND_ON : MotionProtocol.SOUND_OFF));
    }

    /** 唤醒&退出音效开关。 */
    public void wakeSoundEnabled(boolean on) {
        ble.sendMotion(MotionProtocol.frame(
                on ? MotionProtocol.WAKE_SOUND_ON : MotionProtocol.WAKE_SOUND_OFF));
    }

    /** 自适应律动开关（小程序 E7/E8）。 */
    public void selfAdaptiveRhythm(boolean on) {
        ble.sendMotion(MotionProtocol.frame(
                on ? MotionProtocol.SELF_ADAPT_ON : MotionProtocol.SELF_ADAPT_OFF));
    }

    /** 工作模式：声源定位。 */
    public void modeLocalize() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.SOUND_LOCALIZE));
    }

    /** 工作模式：专用词。 */
    public void modeKeyword() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.MODE_KEYWORD));
    }

    /** 特殊指令（默认）。 */
    public void specialDefault() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.SPECIAL_DEFAULT));
    }

    /** 快捷指令。 */
    public void shortcut() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.SHORTCUT));
    }

    /**
     * 播放一个表情：{@code FE 55 10 [cmd] 55 FE} 经运动板下发、显示在眼屏。
     * {@code cmd} 取 {@link MotionProtocol} 的 EXPR_* 常量；也可传任意字节用于实车扫描未公开表情。
     */
    public void playExpression(byte cmd) {
        ble.sendMotion(MotionProtocol.frame(cmd));
    }

    /** 摇头晃脑：运动板内置摆头动作（FE55 字节 0x25），非眼屏表情。 */
    public void wobble() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.MOTION_WOBBLE));
    }

    /** 查询随机动作池（结果经通知回调返回）。 */
    public void randomPoolQuery() {
        ble.sendMotion(MotionProtocol.frame(MotionProtocol.RANDOM_POOL_QUERY));
    }

    /** 设置随机动作池启用位掩码（低 5 位有效）。 */
    public void randomPoolSet(int mask) {
        ble.sendMotion(MotionProtocol.randomPoolSet(mask));
    }

    // ---------------- 氛围灯（a1，机器人底部氛围灯，非眼睛） ----------------

    /** 设置氛围灯颜色。seq 由本类统一生成（1~254），上层无需关心。 */
    public void setEyeColor(int r, int g, int b, int brightness) {
        ble.sendEyes(EyesProtocol.setRgb(r, g, b, brightness, nextSeq()));
    }

    /** 单独设氛围灯亮度。 */
    public void setAmbientBrightness(int brightness) {
        ble.sendEyes(EyesProtocol.setBrightness(brightness, nextSeq()));
    }

    /** 设置某项灵敏度（param 取 EyesProtocol.SENS_*）。 */
    public void setSensitivity(int param, int value) {
        ble.sendEyes(EyesProtocol.sensitivity(param, value, nextSeq()));
    }

    /** RGB 自动亮度开关。 */
    public void ambientAutoBrightness(boolean on) {
        ble.sendEyes(EyesProtocol.rgbAutoBrightness(on, nextSeq()));
    }

    /** 氛围灯校准。 */
    public void ambientCalibrate() {
        ble.sendEyes(EyesProtocol.calibrate(nextSeq()));
    }

    /** 氛围灯重启。 */
    public void ambientReboot() {
        ble.sendEyes(EyesProtocol.reboot(nextSeq()));
    }

    /** 氛围灯复位。 */
    public void ambientReset() {
        ble.sendEyes(EyesProtocol.reset(nextSeq()));
    }

    /** 氛围灯激活。 */
    public void ambientActivate() {
        ble.sendEyes(EyesProtocol.activate(nextSeq()));
    }

    /** 同步时间到氛围灯设备。 */
    public void ambientSyncTime() {
        ble.sendEyes(EyesProtocol.syncTime(nextSeq()));
    }

    /** 查询氛围灯状态（结果经通知回调返回，帧头 0x52）。 */
    public void ambientStatusQuery() {
        ble.sendEyes(EyesProtocol.statusQuery(nextSeq()));
    }

    public void eyesPrevItem() {
        ble.sendEyes(EyesProtocol.command(EyesProtocol.CMD_PREV_ITEM, nextSeq(), null));
    }

    public void eyesNextItem() {
        ble.sendEyes(EyesProtocol.command(EyesProtocol.CMD_NEXT_ITEM, nextSeq(), null));
    }

    private int nextSeq() {
        return 1 + random.nextInt(254); // 1~254
    }
}

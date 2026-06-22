package com.midbows.zkvision.protocol;

/**
 * ET-ROBOT-01 氛围灯设备（a1 协议）纯帧逻辑。
 *
 * <p>注意：该设备是机器人「底部氛围灯」，不是眼睛/表情。
 * 帧格式：{@code A1 [cmd] [seq] [len] [payload...]}。
 * 全项目唯一构帧实现，禁止在别处拼装 a1 字节。命令号与小程序一致（十进制注于括号内）。
 */
public final class EyesProtocol {

    /** 控制字节（帧头）。 */
    public static final byte CTRL = (byte) 0xA1;

    // 命令（括号内为小程序十进制命令号）
    public static final byte CMD_SYNC_TIME = (byte) 0x01;      // 同步时间(1)
    public static final byte CMD_STATUS_QUERY = (byte) 0x05;   // 状态查询(5)
    public static final byte CMD_PREV_ITEM = (byte) 0x06;      // 上一项(6)
    public static final byte CMD_NEXT_ITEM = (byte) 0x07;      // 下一项(7)
    public static final byte CMD_RENAME = (byte) 0x08;         // 改名(8)
    public static final byte CMD_REBOOT = (byte) 0x10;         // 重启(16)
    public static final byte CMD_RESET = (byte) 0x11;          // 复位(17)
    public static final byte CMD_ACTIVATE = (byte) 0x12;       // 激活(18)
    public static final byte CMD_SET_RGB = (byte) 0x14;        // 设置RGB(20)
    public static final byte CMD_SET_BRIGHTNESS = (byte) 0x18; // 亮度(24)
    public static final byte CMD_SENSITIVITY = (byte) 0xCC;    // 灵敏度(204)
    public static final byte CMD_OTA_CAL = (byte) 0xCD;        // 校准/OTA重启(205)
    public static final byte CMD_RGB_MODE = (byte) 0xCE;       // RGB模式(206)
    public static final byte CMD_RGB_AUTO_BRIGHT = (byte) 0xCF;// RGB自动亮度(207)

    // 灵敏度参数项（CMD_SENSITIVITY 的第 1 字节）
    public static final int SENS_TURN_LEFT = 1;  // 左转 3~60
    public static final int SENS_TURN_RIGHT = 2; // 右转 3~60
    public static final int SENS_ACCEL = 3;      // 加速 0~60
    public static final int SENS_BRAKE = 4;      // 刹车 0~60
    public static final int SENS_MUTE = 5;       // 静音 1~999
    public static final int SENS_LOUDNESS = 6;   // 响度 1~999

    // 校准/OTA 的 para 值
    public static final byte OTA_PARA_CALIBRATE = (byte) 0x01; // 校准
    public static final byte OTA_PARA_REBOOT = (byte) 0x02;    // OTA重启

    private EyesProtocol() {
    }

    /**
     * 通用构帧：{@code A1 [cmd] [seq] [len] [payload...]}。
     *
     * @param cmd     命令字节
     * @param seq     序号（1~254，由调用方提供以保证可测试性）
     * @param payload 负载，可为 null
     */
    public static byte[] command(byte cmd, int seq, byte[] payload) {
        int len = payload == null ? 0 : payload.length;
        byte[] out = new byte[4 + len];
        out[0] = CTRL;
        out[1] = cmd;
        out[2] = (byte) seq;
        out[3] = (byte) len;
        for (int i = 0; i < len; i++) {
            out[4 + i] = payload[i];
        }
        return out;
    }

    /**
     * 设置氛围灯 RGB。注意线序为 BGR：{@code A1 14 [seq] 04 [B] [G] [R] [brightness]}。
     *
     * @param r          红
     * @param g          绿
     * @param b          蓝
     * @param brightness 亮度
     * @param seq        序号（1~254）
     */
    public static byte[] setRgb(int r, int g, int b, int brightness, int seq) {
        byte[] payload = new byte[]{(byte) b, (byte) g, (byte) r, (byte) brightness};
        return command(CMD_SET_RGB, seq, payload);
    }

    /** 单独设亮度：{@code A1 18 [seq] 01 [brightness]}。 */
    public static byte[] setBrightness(int brightness, int seq) {
        return command(CMD_SET_BRIGHTNESS, seq, new byte[]{(byte) brightness});
    }

    /**
     * 设置灵敏度：{@code A1 CC [seq] 04 [param] [valL] [valH] 00}。
     * value 取值范围见 {@code SENS_*} 注释；小端 2 字节。
     */
    public static byte[] sensitivity(int param, int value, int seq) {
        byte[] payload = new byte[]{
                (byte) param, (byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF), 0x00};
        return command(CMD_SENSITIVITY, seq, payload);
    }

    /** 校准：{@code A1 CD [seq] 01 01}。 */
    public static byte[] calibrate(int seq) {
        return command(CMD_OTA_CAL, seq, new byte[]{OTA_PARA_CALIBRATE});
    }

    /** 重启：{@code A1 10 [seq] 01 00}。 */
    public static byte[] reboot(int seq) {
        return command(CMD_REBOOT, seq, new byte[]{0x00});
    }

    /** 复位：{@code A1 11 [seq] 00}。 */
    public static byte[] reset(int seq) {
        return command(CMD_RESET, seq, null);
    }

    /** 激活：{@code A1 12 [seq] 00}。 */
    public static byte[] activate(int seq) {
        return command(CMD_ACTIVATE, seq, null);
    }

    /** RGB 自动亮度开关：{@code A1 CF [seq] 01 [0/1]}。 */
    public static byte[] rgbAutoBrightness(boolean on, int seq) {
        return command(CMD_RGB_AUTO_BRIGHT, seq, new byte[]{(byte) (on ? 1 : 0)});
    }

    /** 同步时间（空载，固件取本机时钟）：{@code A1 01 [seq] 00}。 */
    public static byte[] syncTime(int seq) {
        return command(CMD_SYNC_TIME, seq, null);
    }

    /** 状态查询：{@code A1 05 [seq] 00}。响应帧头 0x52。 */
    public static byte[] statusQuery(int seq) {
        return command(CMD_STATUS_QUERY, seq, null);
    }
}

package com.midbows.zkvision.protocol;

/**
 * MIDBOW1S 运动板（FE55 协议）纯帧逻辑。
 *
 * <p>帧格式固定 6 字节：{@code FE 55 10 [cmd] 55 FE}。
 * 全项目唯一构帧实现，禁止在别处拼装 FE55 字节。
 */
public final class MotionProtocol {

    // 帧头 / 帧尾
    private static final byte HEAD0 = (byte) 0xFE;
    private static final byte HEAD1 = (byte) 0x55;
    private static final byte HEAD2 = (byte) 0x10;
    private static final byte TAIL0 = (byte) 0x55;
    private static final byte TAIL1 = (byte) 0xFE;

    // 方向
    public static final byte DIR_UP = (byte) 0x01;
    public static final byte DIR_DOWN = (byte) 0x02;
    public static final byte DIR_LEFT = (byte) 0x03;
    public static final byte DIR_RIGHT = (byte) 0x04;
    public static final byte DIR_CENTER = (byte) 0x05;

    // 电机
    public static final byte MOTOR_ON = (byte) 0xCE;
    public static final byte MOTOR_OFF_PRE = (byte) 0xE4;
    public static final byte MOTOR_OFF = (byte) 0xCF;

    // 律动：F0 直接启动音乐律动，F2 直接关闭（厂家协议，音乐联动用）；E0/E1 为律动减/加速。
    public static final byte RHYTHM_SLOWER = (byte) 0xE0;
    public static final byte RHYTHM_FASTER = (byte) 0xE1;
    public static final byte RHYTHM_ADAPTIVE_ON = (byte) 0xF0;
    public static final byte RHYTHM_ADAPTIVE_OFF = (byte) 0xF2;

    /** 自适应律动开关（小程序按钮用 E7/E8，区别于音乐联动的 F0/F2）。 */
    public static final byte SELF_ADAPT_ON = (byte) 0xE7;
    public static final byte SELF_ADAPT_OFF = (byte) 0xE8;

    // 声音 / 唤醒音
    public static final byte SOUND_LOCALIZE = (byte) 0x00; // 工作模式：声源定位
    public static final byte SOUND_ON = (byte) 0xFA;
    public static final byte SOUND_OFF = (byte) 0xEF;
    public static final byte WAKE_SOUND_ON = (byte) 0xEC;
    public static final byte WAKE_SOUND_OFF = (byte) 0xEB;

    /** 摇头晃脑：运动板内置的一段「摆头」动作（非眼屏表情），与表情同走 FE55 链路。 */
    public static final byte MOTION_WOBBLE = (byte) 0x25;

    // 工作模式 / 快捷 / 特殊指令（小程序）
    public static final byte MODE_KEYWORD = (byte) 0xFC;     // 工作模式：专用词
    public static final byte SPECIAL_DEFAULT = (byte) 0xED;  // 特殊指令默认
    public static final byte SHORTCUT = (byte) 0xFB;         // 快捷指令

    /**
     * 表情命令字节（厂家表）：{@code FE 55 10 [cmd] 55 FE} 直接驱动眼屏表情。
     * 与方向/电机/律动同一条 FE55 链路，由运动板下发、显示在 U0+U1 眼屏。
     * 名称取自小程序「语音指令一览」表情类词。0x13(音乐)仅动作无表情。
     * 注：好热/好冷等未公开字节，用控制台「自定义字节」实车扫描后再补。
     */
    public static final byte EXPR_ANGRY = (byte) 0x06;     // 生气
    public static final byte EXPR_SMILE = (byte) 0x07;     // 笑一个
    public static final byte EXPR_HEART = (byte) 0x08;     // 比个心
    public static final byte EXPR_BLINK = (byte) 0x09;     // 眨眼睛
    public static final byte EXPR_HAPPY = (byte) 0x0A;     // 开心
    public static final byte EXPR_SAD = (byte) 0x0B;       // 难过
    public static final byte EXPR_WINK = (byte) 0x0C;      // 再眨眼睛
    public static final byte EXPR_COOL = (byte) 0x0D;      // 装酷
    public static final byte EXPR_SMIRK = (byte) 0x0E;     // 坏笑
    public static final byte EXPR_FIREWORKS = (byte) 0x0F; // 放烟花
    public static final byte EXPR_RPS = (byte) 0x10;       // 石头剪刀布
    public static final byte EXPR_SCARED = (byte) 0x11;    // 害怕
    public static final byte EXPR_DIZZY = (byte) 0x12;     // 晕头转向
    public static final byte EXPR_MUSIC = (byte) 0x13;     // 音乐（仅动作无表情）
    public static final byte EXPR_SHY = (byte) 0x1A;       // 害羞
    public static final byte EXPR_VALENTINE = (byte) 0x23; // 情人节快乐
    public static final byte EXPR_ROSE = (byte) 0x24;      // 玫瑰花
    public static final byte EXPR_SNACK = (byte) 0x26;     // 吃月饼

    /** 随机动作池：查询当前已启用的动作位掩码。 */
    public static final byte RANDOM_POOL_QUERY = (byte) 0xA0;
    /** 随机动作池设置基址：实际命令 = {@code 0x80 | mask}，mask 取 5 位（0x01~0x1F）。 */
    private static final int RANDOM_POOL_SET_BASE = 0x80;
    public static final int RANDOM_POOL_MASK_MAX = 0x1F;

    private MotionProtocol() {
    }

    /** 构造一条 FE55 帧：{@code FE 55 10 [cmd] 55 FE}。 */
    public static byte[] frame(byte cmd) {
        return new byte[]{HEAD0, HEAD1, HEAD2, cmd, TAIL0, TAIL1};
    }

    /**
     * 随机动作池设置帧：命令字节 = {@code 0x80 | (mask & 0x1F)}。
     * mask 的 5 位分别对应 5 个可启用动作；与小程序一致。
     */
    public static byte[] randomPoolSet(int mask) {
        int cmd = RANDOM_POOL_SET_BASE | (mask & RANDOM_POOL_MASK_MAX);
        return frame((byte) cmd);
    }
}

package com.midbows.zkvision.behavior;

/**
 * 眼睛情绪 → RGB 调色板。全项目唯一「情绪 → 颜色」映射点。
 *
 * <p>注意：具体色值为产品观感默认值，阶段5 设计确认时可调整；
 * 上层只引用情绪枚举，不直接写死颜色。
 */
public enum EyeMood {

    HAPPY(0, 255, 120, 200),
    LISTENING(0, 180, 255, 200),
    THINKING(60, 80, 255, 180),
    SPEAKING(255, 180, 60, 220),
    ALERT(255, 90, 0, 255),
    MUSIC(255, 0, 200, 220),
    IDLE(120, 120, 160, 60),
    /** 上车问候：温暖迎宾。 */
    WELCOME(255, 170, 80, 220),
    /** 冷：冰蓝。 */
    COLD(60, 150, 255, 210),
    /** 热：灼红。 */
    HOT(255, 70, 30, 230),
    /** 困/睡：暗暖。 */
    SLEEP(255, 130, 60, 40),
    /** 夜间：暗紫，犯困。 */
    NIGHT(150, 140, 200, 90),
    /** 白天：明亮。 */
    DAY(255, 250, 235, 200),
    /** 盲区/转向谨慎：警示黄。 */
    CAUTION(255, 200, 0, 230),
    OFF(0, 0, 0, 0);

    public final int r;
    public final int g;
    public final int b;
    public final int brightness;

    EyeMood(int r, int g, int b, int brightness) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.brightness = brightness;
    }
}

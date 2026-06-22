package com.midbows.zkvision.signal;

/**
 * 车辆状态联动的可标定阈值集中点。
 *
 * <p>这些默认值为合理初值，实车标定后只改本文件即可生效，无需动各信号源逻辑。
 * 加速度单位 m/s²（纵向，正=加速、负=刹车，符号实车核对）；温度单位 ℃；SOC 单位 %。
 */
public final class CarThresholds {

    private CarThresholds() {
    }

    /** 纵向加速度超过此值视为「急加速」。 */
    public static final float HARD_ACCEL_MS2 = 2.5f;
    /** 纵向减速度超过此值（绝对值）视为「急刹车」。 */
    public static final float HARD_BRAKE_MS2 = 3.0f;

    /** 车内温度低于此值视为「冷」。 */
    public static final float COLD_BELOW_C = 16.0f;
    /** 车内温度高于此值视为「热」。 */
    public static final float HOT_ABOVE_C = 30.0f;

    /** 充电 SOC 达到此值视为「充满」。 */
    public static final int FULL_SOC_PCT = 99;
}

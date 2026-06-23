package com.midbows.zkvision.signal;

/**
 * 车辆状态联动的可标定阈值<b>出厂默认值</b>集中点。
 *
 * <p>运行时实际取值由 {@code SettingsManager} 读取（用户可在「场景助手」点击预置进入详情调整），
 * 本类只提供初值。加速度为<b>传感器原生量</b>：实车实测纵向加速度读数仅 0.0x 量级（疑似 g 而非
 * m/s²），故急加速/急刹默认阈值取 0.x 量级——原先按 m/s² 设的 2.5/3.0 永远够不到，是「加速无反应」根因。
 * 温度单位 ℃；SOC 单位 %。
 */
public final class CarThresholds {

    private CarThresholds() {
    }

    /** 纵向加速度超过此值视为「急加速」（传感器原生量，约 g；可在预置详情调）。 */
    public static final float HARD_ACCEL = 0.15f;
    /** 纵向减速度超过此值（绝对值）视为「急刹车」（传感器原生量，约 g；可在预置详情调）。 */
    public static final float HARD_BRAKE = 0.20f;

    /** 车内温度低于此值视为「冷」。 */
    public static final float COLD_BELOW_C = 16.0f;
    /** 车内温度高于此值视为「热」。 */
    public static final float HOT_ABOVE_C = 30.0f;

    /** 充电 SOC 达到此值视为「充满」。 */
    public static final int FULL_SOC_PCT = 99;
}

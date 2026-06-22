package com.midbows.zkvision.signal;

/**
 * 纵向加速度 → 急加速/急刹 的纯判定（可单测）。
 *
 * <p>正值=加速、负值=刹车（符号实车核对）。阈值由调用方传入（取自 {@link CarThresholds}），
 * 保持纯逻辑便于单测。
 */
public enum AccelReaction {
    NONE,
    /** 急加速：身体后仰。 */
    ACCEL,
    /** 急刹车：身体前倾。 */
    BRAKE;

    /**
     * @param lonAccel  纵向加速度 m/s²
     * @param hardAccel 急加速阈值（正）
     * @param hardBrake 急刹阈值（正，与负向减速度比较绝对值）
     */
    public static AccelReaction classify(float lonAccel, float hardAccel, float hardBrake) {
        if (lonAccel >= hardAccel) {
            return ACCEL;
        }
        if (lonAccel <= -hardBrake) {
            return BRAKE;
        }
        return NONE;
    }
}

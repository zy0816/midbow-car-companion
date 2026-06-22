package com.midbows.zkvision.signal;

/**
 * 车内温度 → 冷/正常/热 的纯判定（可单测）。
 *
 * <p>取车机 {@code ISensor.SENSOR_TYPE_TEMPERATURE_INDOOR} 的实测温度（℃）。
 * 阈值由调用方传入（取自 {@link CarThresholds}）。
 */
public enum CabinTempReaction {
    COLD,
    NORMAL,
    HOT;

    public static CabinTempReaction classify(float tempC, float coldBelow, float hotAbove) {
        if (tempC <= coldBelow) {
            return COLD;
        }
        if (tempC >= hotAbove) {
            return HOT;
        }
        return NORMAL;
    }
}

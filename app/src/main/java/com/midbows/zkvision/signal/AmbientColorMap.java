package com.midbows.zkvision.signal;

/**
 * 车机氛围灯颜色值 → RGB 的纯映射（可单测）。
 *
 * <p>实车标定结论：真实「当前色」在 {@code IAmbienceLight.SETTING_FUNC_AMBIENCE_LIGHT_MAINZONES_COLOR_SET}
 * (0x2a040100)，取值是 Android 打包色 {@code 0xAARRGGBB}（非旧的 MOOD_LIGHT_COLOR 命名枚举——
 * 那个 funcId 0x2a060200 在本车永远返回 0xFF/255 无效）。这里按位手工解码（不依赖
 * {@code android.graphics.Color}，便于 JVM 单测），并把旧的无效哨兵值（≤0xFF：0 / 255 等）视为熄灭。
 */
public final class AmbientColorMap {

    private AmbientColorMap() {
    }

    /**
     * 把车机氛围灯打包色值（0xAARRGGBB）解为 RGB 三元组。
     *
     * <p>读失败（{@link Integer#MIN_VALUE}）或 ≤0xFF 的旧无效哨兵（0=关、255=未填）返回
     * {@code null}（不点灯/熄灭）；其余按高位提取 R/G/B（忽略 alpha，亮度另由上层给定）。
     */
    public static int[] toRgb(int packed) {
        if (packed == Integer.MIN_VALUE || (packed & 0xFFFFFF) == 0 || (packed >= 0 && packed <= 0xFF)) {
            return null;
        }
        int r = (packed >> 16) & 0xFF;
        int g = (packed >> 8) & 0xFF;
        int b = packed & 0xFF;
        if (r == 0 && g == 0 && b == 0) {
            return null;
        }
        return new int[]{r, g, b};
    }
}

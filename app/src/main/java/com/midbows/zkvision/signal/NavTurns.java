package com.midbows.zkvision.signal;

/**
 * 高德车机导航诱导图标 → 左/右转判定（纯逻辑，可单测）。
 *
 * <p>ICON：2-左转 3-右转 4-左前方 5-右前方 6-左后方 7-右后方 8-左转掉头。
 */
public final class NavTurns {

    private NavTurns() {
    }

    public static boolean isLeft(int icon) {
        return icon == 2 || icon == 4 || icon == 6 || icon == 8;
    }

    public static boolean isRight(int icon) {
        return icon == 3 || icon == 5 || icon == 7;
    }
}

package com.midbows.zkvision.signal;

/**
 * 分区唤醒方向 → 机器人转向座位 的纯映射（可单测）。
 *
 * <p>车机系统属性 {@code speech.wakeup_direction_local} 的取值：
 * 1=主驾 2=副驾 3=左后 4=右后 5/6=三排。机器人在前舱，只能左右转，
 * 故按左右两侧归并：左侧(主驾/左后)→左，右侧(副驾/右后)→右，其余居中。
 *
 * <p>输出语义对齐 {@code BehaviorEngine.onVoiceWakeup}：1=左 2=右 0=居中。
 */
public final class WakeupDirection {

    public static final int DRIVER = 1;
    public static final int COPILOT = 2;
    public static final int REAR_LEFT = 3;
    public static final int REAR_RIGHT = 4;

    public static final int SEAT_CENTER = 0;
    public static final int SEAT_LEFT = 1;
    public static final int SEAT_RIGHT = 2;

    private WakeupDirection() {
    }

    /** 把系统属性方向值映射为机器人转向座位（1=左 2=右 0=居中）。 */
    public static int toSeat(int direction) {
        switch (direction) {
            case DRIVER:
            case REAR_LEFT:
                return SEAT_LEFT;
            case COPILOT:
            case REAR_RIGHT:
                return SEAT_RIGHT;
            default:
                return SEAT_CENTER;
        }
    }
}

package com.midbows.zkvision.behavior;

/**
 * 行为优先级仲裁（纯逻辑，可单测）。
 *
 * <p>高优先级事件可打断低优先级；同级可刷新。把仲裁从 Android 时序中剥离，
 * 便于单元测试，{@link BehaviorEngine} 只负责把仲裁结果接到时序与下发。
 */
public final class PriorityArbiter {

    public static final int IDLE = 0;
    public static final int MUSIC = 10;
    /** 转向灯探头（轻量、可被打断）。 */
    public static final int PEEK = 15;
    public static final int TTS = 20;
    public static final int NAV = 30;
    /** 急加速/急刹反应。 */
    public static final int ACCEL = 35;
    public static final int WELCOME = 40;
    /** 盲区扭头。 */
    public static final int BLINDSPOT = 45;
    public static final int WAKEUP = 50;
    /** 胎压告警。 */
    public static final int TIRE = 55;
    /** 碰撞惊吓（最高，压制一切）。 */
    public static final int COLLISION = 60;

    private int current = IDLE;

    /** 若该优先级 ≥ 当前，则接受并置为当前，返回 true；否则忽略返回 false。 */
    public synchronized boolean accept(int priority) {
        if (priority >= current) {
            current = priority;
            return true;
        }
        return false;
    }

    public synchronized int current() {
        return current;
    }

    public synchronized void reset() {
        current = IDLE;
    }

    /** 仅当当前正处于给定优先级时回落到 IDLE，避免误降已被更高事件抬高的优先级。 */
    public synchronized boolean resetIfAt(int priority) {
        if (current == priority) {
            current = IDLE;
            return true;
        }
        return false;
    }
}

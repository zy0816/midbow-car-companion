package com.midbows.zkvision.automation;

/**
 * 场景助手规则的比较运算符（纯逻辑，可单测）。
 *
 * <p>布尔/枚举型触发用 {@link #EQ}/{@link #NE}；连续量（加速度/温度/SOC）用
 * {@link #GT}/{@link #LT} 配阈值。
 */
public enum Comparator {
    EQ("等于"),
    NE("不等于"),
    GT("大于"),
    LT("小于");

    public final String label;

    Comparator(String label) {
        this.label = label;
    }

    public boolean matchInt(int current, int target) {
        switch (this) {
            case EQ:
                return current == target;
            case NE:
                return current != target;
            case GT:
                return current > target;
            case LT:
                return current < target;
            default:
                return false;
        }
    }

    public boolean matchFloat(float current, float target) {
        switch (this) {
            case EQ:
                return current == target;
            case NE:
                return current != target;
            case GT:
                return current > target;
            case LT:
                return current < target;
            default:
                return false;
        }
    }
}

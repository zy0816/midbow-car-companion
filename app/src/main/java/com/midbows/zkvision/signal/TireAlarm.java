package com.midbows.zkvision.signal;

/**
 * 胎压系统状态 → 是否告警 的纯判定（可单测）。
 *
 * <p>取车机 {@code ITireSensor.TIRE_TPMS_SYS_STATES} 的系统态。用字面常量复刻 ecarx 值，
 * 保持纯逻辑。NO_WARN/系统故障/不可用 不算「胎压告警」，仅 CMN/单胎 WARN 视为告警。
 */
public final class TireAlarm {

    static final int TPMS_STATES_NO_WARN = 5259265;
    static final int TPMS_STATES_CMN_WARN = 5259266;
    static final int TPMS_STATES_FL_WARN = 5259267;
    static final int TPMS_STATES_FR_WARN = 5259268;
    static final int TPMS_STATES_RL_WARN = 5259269;
    static final int TPMS_STATES_RR_WARN = 5259270;

    private TireAlarm() {
    }

    public static boolean isWarning(int tpmsState) {
        switch (tpmsState) {
            case TPMS_STATES_CMN_WARN:
            case TPMS_STATES_FL_WARN:
            case TPMS_STATES_FR_WARN:
            case TPMS_STATES_RL_WARN:
            case TPMS_STATES_RR_WARN:
                return true;
            default:
                return false;
        }
    }
}

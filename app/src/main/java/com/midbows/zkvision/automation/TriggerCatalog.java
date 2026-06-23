package com.midbows.zkvision.automation;

import com.ecarx.xui.adaptapi.car.hev.ICharging;
import com.ecarx.xui.adaptapi.car.sensor.ISensor;
import com.ecarx.xui.adaptapi.car.sensor.ISensorEvent;
import com.ecarx.xui.adaptapi.car.sensor.ITireSensor;
import com.ecarx.xui.adaptapi.car.vehicle.IADAS;
import com.ecarx.xui.adaptapi.car.vehicle.IBcm;
import com.ecarx.xui.adaptapi.car.vehicle.IDriveMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义场景规则可选「触发源（车机指令）」目录。
 *
 * <p>每个 {@link TriggerDef} 把一个 ecarx 车身功能/传感封装成对用户友好的可选项，供规则编辑页
 * 下拉选择；{@code RuleEngine} 据 {@link #kind}/{@link #id} 订阅对应监听，回调值用规则的
 * {@link Comparator} 判定。值类型 {@link ValueKind} 决定编辑页是开关/枚举下拉/数值输入。
 */
public final class TriggerCatalog {

    /** 监听种类：车身功能值 / 传感离散态 / 传感连续量。 */
    public enum Kind { FUNCTION, SENSOR_EVENT, SENSOR_VALUE }

    /** 取值形态：开关(ON/OFF) / 枚举(下拉) / 数值(输入)。 */
    public enum ValueKind { BOOL, ENUM, NUMBER }

    /** 枚举型可选值。 */
    public static final class Option {
        public final int value;
        public final String label;

        public Option(int value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    public static final class TriggerDef {
        public final String key;
        public final String name;
        public final Kind kind;
        public final int id;
        public final int zone;
        public final ValueKind valueKind;
        public final List<Option> options;
        /** 连续量是否为浮点（加速度/温度）；整型功能/传感为 false。 */
        public final boolean floatValue;

        TriggerDef(String key, String name, Kind kind, int id, ValueKind valueKind,
                   List<Option> options, boolean floatValue) {
            this.key = key;
            this.name = name;
            this.kind = kind;
            this.id = id;
            this.zone = -1;
            this.valueKind = valueKind;
            this.options = options == null ? Collections.emptyList() : options;
            this.floatValue = floatValue;
        }
    }

    private static final int ON = com.ecarx.xui.adaptapi.car.base.ICarFunction.COMMON_VALUE_ON;
    private static final int OFF = com.ecarx.xui.adaptapi.car.base.ICarFunction.COMMON_VALUE_OFF;

    private static final List<TriggerDef> ALL = build();

    private TriggerCatalog() {
    }

    public static List<TriggerDef> all() {
        return ALL;
    }

    public static TriggerDef byKey(String key) {
        for (TriggerDef d : ALL) {
            if (d.key.equals(key)) {
                return d;
            }
        }
        return null;
    }

    private static List<Option> onOff() {
        List<Option> l = new ArrayList<>();
        l.add(new Option(ON, "开/有"));
        l.add(new Option(OFF, "关/无"));
        return l;
    }

    /** 挡位：传感事件回传 ISensorEvent.GEAR_* 值，列常用 P/R/N/D。 */
    private static List<Option> gearOptions() {
        List<Option> l = new ArrayList<>();
        l.add(new Option(ISensorEvent.GEAR_PARK, "P 驻车"));
        l.add(new Option(ISensorEvent.GEAR_REVERSE, "R 倒车"));
        l.add(new Option(ISensorEvent.GEAR_NEUTRAL, "N 空挡"));
        l.add(new Option(ISensorEvent.GEAR_DRIVE, "D 前进"));
        return l;
    }

    /** 座椅占用：ISensorEvent.SEAT_OCCUPATION_STATUS_*。 */
    private static List<Option> seatOptions() {
        List<Option> l = new ArrayList<>();
        l.add(new Option(ISensorEvent.SEAT_OCCUPATION_STATUS_OCCUPIED, "有人"));
        l.add(new Option(ISensorEvent.SEAT_OCCUPATION_STATUS_NONE, "无人"));
        return l;
    }

    /** 胎压系统态：ITireSensor.TPMS_STATES_*。 */
    private static List<Option> tireOptions() {
        List<Option> l = new ArrayList<>();
        l.add(new Option(ITireSensor.TPMS_STATES_NO_WARN, "正常"));
        l.add(new Option(ITireSensor.TPMS_STATES_CMN_WARN, "通用告警"));
        l.add(new Option(ITireSensor.TPMS_STATES_FL_WARN, "左前告警"));
        l.add(new Option(ITireSensor.TPMS_STATES_FR_WARN, "右前告警"));
        l.add(new Option(ITireSensor.TPMS_STATES_RL_WARN, "左后告警"));
        l.add(new Option(ITireSensor.TPMS_STATES_RR_WARN, "右后告警"));
        l.add(new Option(ITireSensor.TPMS_STATES_SYS_FAILURE, "系统故障"));
        return l;
    }

    /** 驾驶模式：DM_FUNC_DRIVE_MODE_SELECT 回传 IDriveMode.DRIVE_MODE_SELECTION_* 值（车型相关，列常用项）。 */
    private static List<Option> driveModeOptions() {
        List<Option> l = new ArrayList<>();
        l.add(new Option(IDriveMode.DRIVE_MODE_SELECTION_ECO, "经济"));
        l.add(new Option(IDriveMode.DRIVE_MODE_SELECTION_COMFORT, "舒适"));
        l.add(new Option(IDriveMode.DRIVE_MODE_SELECTION_NORMAL, "标准"));
        l.add(new Option(IDriveMode.DRIVE_MODE_SELECTION_DYNAMIC, "运动"));
        l.add(new Option(IDriveMode.DRIVE_MODE_SPORT_PLUS, "运动+"));
        l.add(new Option(IDriveMode.DRIVE_MODE_SELECTION_SNOW, "雪地"));
        l.add(new Option(IDriveMode.DRIVE_MODE_SELECTION_OFFROAD, "越野"));
        return l;
    }

    private static List<TriggerDef> build() {
        List<TriggerDef> l = new ArrayList<>();
        // —— 车身功能 ——
        l.add(new TriggerDef("door", "车门", Kind.FUNCTION, IBcm.BCM_FUNC_DOOR,
                ValueKind.BOOL, onOff(), false));
        l.add(new TriggerDef("charging", "充电状态", Kind.FUNCTION, ICharging.CHARGE_FUNC_CHARGING,
                ValueKind.BOOL, onOff(), false));
        l.add(new TriggerDef("soc", "充电电量(%)", Kind.FUNCTION, ICharging.CHARGE_FUNC_CHARGING_SOC,
                ValueKind.NUMBER, null, false));
        l.add(new TriggerDef("fcw", "前向碰撞预警", Kind.FUNCTION, IADAS.SETTING_FUNC_FORWARD_COLLISION_WARN,
                ValueKind.BOOL, onOff(), false));
        List<Option> bsd = new ArrayList<>();
        bsd.add(new Option(0, "无"));
        bsd.add(new Option(1, "左侧"));
        bsd.add(new Option(2, "右侧"));
        bsd.add(new Option(3, "两侧"));
        l.add(new TriggerDef("bsd", "盲区告警", Kind.FUNCTION, IADAS.SETTING_FUNC_BLIND_SPOT_DETECTION_WARNING,
                ValueKind.ENUM, bsd, false));
        l.add(new TriggerDef("turn_left", "左转向灯", Kind.FUNCTION, IBcm.BCM_FUNC_LIGHT_LEFT_TRUN_SIGNAL,
                ValueKind.BOOL, onOff(), false));
        l.add(new TriggerDef("turn_right", "右转向灯", Kind.FUNCTION, IBcm.BCM_FUNC_LIGHT_RIGHT_TRUN_SIGNAL,
                ValueKind.BOOL, onOff(), false));
        l.add(new TriggerDef("drive_mode", "驾驶模式", Kind.FUNCTION, IDriveMode.DM_FUNC_DRIVE_MODE_SELECT,
                ValueKind.ENUM, driveModeOptions(), false));
        // —— 传感离散态 ——
        l.add(new TriggerDef("seat_driver", "主驾就座", Kind.SENSOR_EVENT,
                ISensor.SENSOR_TYPE_SEAT_OCCUPATION_STATUS_DRIVER, ValueKind.ENUM, seatOptions(), false));
        l.add(new TriggerDef("seat_passenger", "副驾就座", Kind.SENSOR_EVENT,
                ISensor.SENSOR_TYPE_SEAT_OCCUPATION_STATUS_PASSENGER, ValueKind.ENUM, seatOptions(), false));
        l.add(new TriggerDef("gear", "挡位", Kind.SENSOR_EVENT, ISensor.SENSOR_TYPE_GEAR,
                ValueKind.ENUM, gearOptions(), false));
        l.add(new TriggerDef("tire", "胎压状态", Kind.SENSOR_EVENT, ITireSensor.TIRE_TPMS_SYS_STATES,
                ValueKind.ENUM, tireOptions(), false));
        // —— 传感连续量（阈值，浮点）——
        l.add(new TriggerDef("accel", "纵向加速度(约g,静止≈0.0x)", Kind.SENSOR_VALUE,
                ISensor.SENSOR_TYPE_SPEED_LON_ACCELERATION, ValueKind.NUMBER, null, true));
        l.add(new TriggerDef("temp", "车内温度(℃)", Kind.SENSOR_VALUE,
                ISensor.SENSOR_TYPE_TEMPERATURE_INDOOR, ValueKind.NUMBER, null, true));
        return l;
    }
}
